package com.ghb.ecommerceflashsalesystem.stream.consumer;


import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.dto.message.SeckillOrderMessage;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * @Component（Spring 提供）
 *
 * 作用：将当前类注册为 Spring 容器中的 Bean（受 Spring 管理）。
 *
 * 效果：Spring 启动时会扫描到这个类，创建它的实例放进 IoC 容器。
 *
 * @RequiredArgsConstructor（Lombok 提供）
 *
 * 作用：在编译期为所有 final 修饰的字段（或 @NonNull 字段）生成一个全参构造方法。
 *
 * 效果：如果类里有 private final UserService userService;，
 * 它会自动生成 public Xxx(UserService userService) 构造器。
 */
public class SeckillOrderConsumer {
    private final RedisTemplate<String, Object> redisTemplate;
    private final SeckillOrderMapper seckillOrderMapper;

    private static final String STREAM_KEY = CacheKeyConstant.SECKILL_ORDER_STREAM;
    private static final String GROUP_NAME = CacheKeyConstant.SECKILL_ORDER_GROUP;
    // 【复盘】曾用 System.getenv("HOSTNAME")，Windows 无此环境变量会拼出 consumer-null-xxx；改 UUID 保证唯一
    private static final String CONSUMER_NAME = "consumer-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    /**
     * @PostConstruct
     * 修饰一个无参、无返回值、非静态的 void 方法，表示这个方法需要在 Bean 初始化时被自动调用。
     */
    @PostConstruct
    public void init() {
        ensureGroup() ;
        // 启动后自动消费，但通过配置开关控制是否启用，这里只做组创建
        // 实际消费由调度器或手动触发，避免测试干扰
        log.info("消费者组初始化完成: group={}, consumer={}", GROUP_NAME, CONSUMER_NAME);
    }

    /**
     * 确保消费者组存在：XGROUP CREATE key group 0 MKSTREAM
     *
     * 【复盘】v1 用 opsForStream().createGroup(key, group)：Redis 的 XGROUP CREATE 在不带 MKSTREAM 时，
     * 若 Stream 键不存在会直接报错（ERR The XGROUP subcommand requires the key to exist），
     * 导致 @PostConstruct 初始化失败、Spring 上下文起不来（测试表现为"全量 0.001s 全 Error"）。
     * 修正：走底层连接的 xGroupCreate(..., ReadOffset.from("0"), mkStream=true)，流不存在自动创建；组已存在抛 BUSYGROUP 则忽略。
     */
    public void ensureGroup() {
        try {
            String result = redisTemplate.execute((RedisCallback<String>) connection -> {
                // XGROUP CREATE key group 0 MKSTREAM：流不存在会自动创建
                try {
                    connection.execute("XGROUP",
                            "CREATE".getBytes(StandardCharsets.UTF_8),
                            STREAM_KEY.getBytes(StandardCharsets.UTF_8),
                            GROUP_NAME.getBytes(StandardCharsets.UTF_8),
                            "0".getBytes(StandardCharsets.UTF_8),
                            "MKSTREAM".getBytes(StandardCharsets.UTF_8));
                    return "created";
                } catch (RuntimeException e) {
                    // 【复盘】BUSYGROUP 会被 Spring Data Redis 包成外层 RedisSystemException（message="Error in execution"），
                    // 真实原因在 cause 链里（io.lettuce.core.RedisBusyException: BUSYGROUP Consumer Group name already exists）。
                    // 曾只检查 e.getMessage() 导致漏判、@PostConstruct 抛异常使后续 context 全部失败（failure threshold）。
                    // 修正：沿 cause 链查找 BUSYGROUP，命中即视为"组已存在"。
                    if (containsBusyGroup(e)) {
                        return "exists";
                    }
                    throw e;
                }
            });
            log.info("created".equals(result)
                    ? "消费者组创建成功: stream={}, group={}" : "消费者组已存在: stream={}, group={}",
                    STREAM_KEY, GROUP_NAME);
        } catch (Exception e) {
            log.error("创建消费者组失败", e);
            throw new RuntimeException("消费者组初始化失败", e);
        }
    }

    /** 沿异常 cause 链判断是否因 BUSYGROUP（组已存在） */
    private boolean containsBusyGroup(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }
    /**
     * 消费待处理消息（手动拉取模式，供测试和调度使用）
     * 拉取 COUNT 条消息并逐一处理
     */
    public void consumePending(int count) {
        StreamOperations<String, Object, Object> opsForStream = redisTemplate.opsForStream();
        // XREADGROUP GROUP group consumer COUNT count STREAMS stream >
        List<MapRecord<String, Object, Object>> records = opsForStream.read(
                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                StreamReadOptions.empty().count(count),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
        );
        
        if (records == null || records.isEmpty()){
            log.debug("无等待消息");
            return;
        }
        
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
    }

    /**
     * 单条消息处理：解析 → 落库 → XACK
     */
    private void handle(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();
        log.info("开始处理消息, messageId={}", messageId);

        try {
            // 将 Map 转成 SeckillOrderMessage
            // 【复盘】读回 MapRecord 的 value 经序列化后字段多为 String（如 "100"），
            //   曾用 (Long)/(Number) 直接强转抛 ClassCastException（String cannot be cast to Long），
            //   故统一走 toLong/toStr 安全转换
            Map<Object, Object> body = record.getValue();
            SeckillOrderMessage message = SeckillOrderMessage.builder()
                    .activityId(toLong(body.get("activityId")))
                    .productId(toLong(body.get("productId")))
                    .userId(toLong(body.get("userId")))
                    .orderNo(toStr(body.get("orderNo")))
                    .seckillPrice(toLong(body.get("seckillPrice")))
                    .requestTime(toLong(body.get("requestTime")))
                    .build();

            // 幂等检查：是否已存在订单（按 orderNo 或 activityId+userId）
            // 先按 orderNo 查
            SeckillOrder existing = seckillOrderMapper.selectByOrderNo(message.getOrderNo());
            if (existing != null){
                log.info("订单已存在，幂等跳过: orderNo={}", message.getOrderNo());
                ack(record);
                return;
            }

            // 构造订单实体
            SeckillOrder order = new SeckillOrder();
            order.setOrderNo(message.getOrderNo());
            order.setActivityId(message.getActivityId());
            order.setProductId(message.getProductId());
            order.setUserId(message.getUserId());
            order.setSeckillPrice(message.getSeckillPrice());
            order.setStatus(1); // CREATED
            order.setStreamMessageId(messageId);

            // 插入，捕获唯一键冲突（幂等兜底）
            seckillOrderMapper.insert(order);
            log.info("订单落库成功: orderNo={}", message.getOrderNo());
            ack(record);
        }catch (DuplicateKeyException e){
            // 唯一键冲突，说明订单已存在（可能是并发提交），视为幂等
            log.warn("订单唯一键冲突，幂等跳过: orderNo={}, 异常={}",
                    getOrderNoFromRecord(record), e.getMessage());
            ack(record);
        } catch (Exception e){
            log.error("处理消息失败，messageId={}", messageId, e);
            // 不 ACK，消息会留在 PEL 等待重试，Day4 再做重试策略
            // 这里仅记录，不抛出异常，避免影响其他消息
        }
    }

    private Object getOrderNoFromRecord(MapRecord<String, Object, Object> record) {

        Object orderNo = record.getValue().get("orderNo");
        return orderNo != null ? orderNo.toString() : "unknown";
    }

    /** Map 值安全转 Long：兼容 String / Number / null */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    /** Map 值安全转 String */
    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }
    private void ack(MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(GROUP_NAME, record);
        log.debug("消息已确认: {}", record.getId().getValue());
    }
}
