package com.ghb.ecommerceflashsalesystem.stream.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.dto.message.SeckillOrderMessage;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillMessageLog;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.MessageLogStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillMessageLogMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.service.rank.SeckillRankService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 秒杀订单 Stream 消费者（第 3 阶段 Day 3/4）
 *
 * 职责：消费者组读取 seckill:order:stream → 幂等落库 seckill_order → XACK；
 * 失败路径：seckill_message_log 记录 retry_count/error → 未达阈值留 PEL 供重试 → 达阈值进死信流。
 *
 * 【Day 4 重构复盘】v1 在 handle 里嵌套两层 try，外层 try 缺 catch/finally 直接编译失败；
 * 且业务落库分支丢失了 seckillOrderMapper.insert(order)（只 ACK 不落库）、
 * sendToDeadLetter 引用了未定义变量 errorMsg/originalMessageId。本版重写为单一职责的干净实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillMessageLogMapper seckillMessageLogMapper;

    private final SeckillRankService rankService;
    private static final String STREAM_KEY = CacheKeyConstant.SECKILL_ORDER_STREAM;
    private static final String GROUP_NAME = CacheKeyConstant.SECKILL_ORDER_GROUP;
    // 【复盘】曾用 System.getenv("HOSTNAME")，Windows 无此环境变量会拼出 consumer-null-xxx；改 UUID 保证唯一
    private static final String CONSUMER_NAME = "consumer-" + UUID.randomUUID().toString().substring(0, 8);
    private static final long MAX_RETRY = CacheKeyConstant.MESSAGE_MAX_RETRY;
    private static final int MAX_ERROR_MSG_LEN = 1023; // 与 seckill_message_log.error_message VARCHAR(1024) 对齐

    /** Bean 初始化：确保消费者组存在（不自动消费，避免测试被后台线程干扰） */
    @PostConstruct
    public void init() {
        ensureGroup();
        log.info("消费者组初始化完成: group={}, consumer={}", GROUP_NAME, CONSUMER_NAME);
    }

    /**
     * 确保消费者组存在：XGROUP CREATE key group 0 MKSTREAM
     *
     * 【复盘】BUSYGROUP 会被 Spring Data Redis 包成外层 RedisSystemException（message="Error in execution"），
     * 真实原因在 cause 链里（RedisBusyException: BUSYGROUP ...）。曾只检查外层 message 漏判，
     * 导致第二个 context 建组时 init 抛异常 → 全量测试 failure threshold 连锁失败。故沿 cause 链识别。
     */
    public void ensureGroup() {
        try {
            String result = redisTemplate.execute((RedisCallback<String>) connection -> {
                try {
                    connection.execute("XGROUP",
                            "CREATE".getBytes(StandardCharsets.UTF_8),
                            STREAM_KEY.getBytes(StandardCharsets.UTF_8),
                            GROUP_NAME.getBytes(StandardCharsets.UTF_8),
                            "0".getBytes(StandardCharsets.UTF_8),
                            "MKSTREAM".getBytes(StandardCharsets.UTF_8));
                    return "created";
                } catch (RuntimeException e) {
                    if (containsBusyGroup(e)) {
                        return "exists"; // 组已存在，忽略
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
     * 消费新消息（XREADGROUP ... STREAMS stream &gt;）——只读还没投递给本组的新消息
     */
    public void consumePending(int count) {
        List<MapRecord<String, Object, Object>> records = readRecords(ReadOffset.lastConsumed(), count);
        if (records == null || records.isEmpty()) {
            log.debug("无等待消息");
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
    }

    /**
     * 消费重试消息（XREADGROUP ... STREAMS stream 0）——从 PEL 读回未 ACK 的消息重新处理。
     * &gt; 只读新消息，0 读 PEL；失败未达阈值不 ACK 的消息就在这里被重新拾起。
     */
    public void consumerRetry(int count) {
        List<MapRecord<String, Object, Object>> records = readRecords(ReadOffset.from("0"), count);
        if (records == null || records.isEmpty()) {
            log.debug("PEL 无待重试消息");
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
    }

    private List<MapRecord<String, Object, Object>> readRecords(ReadOffset readOffset, int count) {
        return redisTemplate.opsForStream().read(
                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                StreamReadOptions.empty().count(count),
                StreamOffset.create(STREAM_KEY, readOffset)
        );
    }

    /**
     * 单条消息处理：解析 → 消息日志 → 幂等落库 → XACK；失败按 retry 计数进 PEL/死信
     */
    private void handle(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();
        log.info("开始处理消息, messageId={}", messageId);
        try {
            // 1. 解析消息（读回字段为 String，安全转换，不依赖强转）
            SeckillOrderMessage message = parseMessage(record);

            // 2. 获取或创建消息日志（幂等，状态 PENDING）
            SeckillMessageLog logEntity = getOrCreateMessageLog(messageId, message);

            // 终态（已成功/已死信）直接 ACK，防止重复处理
            int status = logEntity.getStatus() == null ? 0 : logEntity.getStatus();
            if (status == MessageLogStatusEnum.SUCCESS.getCode()
                    || status == MessageLogStatusEnum.DEAD.getCode()) {
                log.info("消息已是终态，直接ACK: messageId={}", messageId);
                // SUCCESS 终态补录一次榜单（NX 幂等：兼容早期"已落库但未入榜"的消息，重复调用不脏榜）
                if (status == MessageLogStatusEnum.SUCCESS.getCode()) {
                    rankService.recordSuccess(message.getActivityId(), message.getUserId(), message.getRequestTime());
                }
                ack(record);
                return;
            }

            // 3. 业务落库（幂等：orderNo 已存在即视为成功，不再重复插入）
            try {
                if (seckillOrderMapper.selectByOrderNo(message.getOrderNo()) == null) {
                    SeckillOrder order = new SeckillOrder();
                    order.setOrderNo(message.getOrderNo());
                    order.setActivityId(message.getActivityId());
                    order.setProductId(message.getProductId());
                    order.setUserId(message.getUserId());
                    order.setSeckillPrice(message.getSeckillPrice());
                    order.setStatus(1); // CREATED
                    order.setStreamMessageId(messageId);
                    seckillOrderMapper.insert(order); // 必须真正落库（曾因重构丢失此句导致只 ACK 不建单）
                }
                // 订单成功（首次 insert 或幂等命中已存在单）即入榜；NX 幂等，重复触发不重复加分
                rankService.recordSuccess(message.getActivityId(), message.getUserId(), message.getRequestTime());
                markSuccess(logEntity, message.getOrderNo());
                ack(record);
            } catch (DuplicateKeyException e) {
                // DB 唯一键兜底（uk_activity_user / uk_order_no）：视为已处理，同属订单成功路径
                log.warn("订单唯一键冲突，幂等处理: orderNo={}", message.getOrderNo());
                rankService.recordSuccess(message.getActivityId(), message.getUserId(), message.getRequestTime());
                markSuccess(logEntity, message.getOrderNo());
                ack(record);
            } catch (Exception e) {
                handleFailure(record, message, messageId, logEntity, e);
            }
        } catch (Exception e) {
            // R5 修复：解析/日志异常（如脏字段导致的 NumberFormatException）也必须计数并最终转死信，
            // 否则毒消息永远留在 PEL 每轮重试、PEL 无限增长。这里宽松解析消息体 → 走统一 handleFailure。
            handleUnrecoverable(record, messageId, e);
        }
    }

    /**
     * 处理外层异常（解析失败/字段脏数据等）：
     * 宽松解析消息体（坏字段置 null，不抛），能建日志则进入 handleFailure 计数，
     * 达到阈值转死信 + ACK，杜绝毒消息无限滞留 PEL。
     */
    private void handleUnrecoverable(MapRecord<String, Object, Object> record,
                                     String messageId, Exception e) {
        log.error("处理消息异常（解析/字段异常），messageId={}", messageId, e);
        try {
            SeckillOrderMessage fallback = parseMessageLenient(record);
            SeckillMessageLog logEntity = getOrCreateMessageLog(messageId, fallback);
            handleFailure(record, fallback, messageId, logEntity, e);
        } catch (Exception inner) {
            // 连日志表都不可用（基础设施故障）：保留 PEL，待恢复后由 consumerRetry 再拾起
            log.error("处理消息异常且无法写入消息日志（基础设施不可用），messageId={}", messageId, inner);
        }
    }

    /** 宽松解析：坏字段置 null 不抛（区别于严格 parseMessage） */
    private SeckillOrderMessage parseMessageLenient(MapRecord<String, Object, Object> record) {
        Map<Object, Object> body = record.getValue();
        return SeckillOrderMessage.builder()
                .activityId(lenientLong(body.get("activityId")))
                .productId(lenientLong(body.get("productId")))
                .userId(lenientLong(body.get("userId")))
                .orderNo(toStr(body.get("orderNo")))
                .seckillPrice(lenientLong(body.get("seckillPrice")))
                .requestTime(lenientLong(body.get("requestTime")))
                .build();
    }

    /** 解析消息字段（生产者按明文 Map 投递，字段为 String） */
    private SeckillOrderMessage parseMessage(MapRecord<String, Object, Object> record) {
        Map<Object, Object> body = record.getValue();
        return SeckillOrderMessage.builder()
                .activityId(toLong(body.get("activityId")))
                .productId(toLong(body.get("productId")))
                .userId(toLong(body.get("userId")))
                .orderNo(toStr(body.get("orderNo")))
                .seckillPrice(toLong(body.get("seckillPrice")))
                .requestTime(toLong(body.get("requestTime")))
                .build();
    }

    /** 按 stream_message_id 查询；不存在则插入 PENDING（并发重复插入用唯一键兜底后重查） */
    private SeckillMessageLog getOrCreateMessageLog(String messageId, SeckillOrderMessage message) {
        SeckillMessageLog logEntity = seckillMessageLogMapper.selectOne(
                new LambdaQueryWrapper<SeckillMessageLog>()
                        .eq(SeckillMessageLog::getStreamMessageId, messageId));
        if (logEntity != null) {
            return logEntity;
        }
        SeckillMessageLog entity = new SeckillMessageLog();
        entity.setStreamKey(STREAM_KEY);
        entity.setStreamMessageId(messageId);
        entity.setConsumerGroup(GROUP_NAME);
        entity.setConsumerName(CONSUMER_NAME);
        entity.setOrderNo(message.getOrderNo());
        entity.setActivityId(message.getActivityId());
        entity.setProductId(message.getProductId());
        entity.setUserId(message.getUserId());
        entity.setRetryCount(0);
        entity.setStatus(MessageLogStatusEnum.PENDING.getCode());
        try {
            seckillMessageLogMapper.insert(entity);
            return entity;
        } catch (DuplicateKeyException e) {
            // 并发已插入，重查返回
            return seckillMessageLogMapper.selectOne(
                    new LambdaQueryWrapper<SeckillMessageLog>()
                            .eq(SeckillMessageLog::getStreamMessageId, messageId));
        }
    }

    private void markSuccess(SeckillMessageLog logEntity, String orderNo) {
        logEntity.setStatus(MessageLogStatusEnum.SUCCESS.getCode());
        seckillMessageLogMapper.updateById(logEntity);
        log.info("订单落库成功并ACK, orderNo={}", orderNo);
    }

    /** 失败处理：retry+1 记 FAILED；未达阈值不 ACK 留 PEL；达阈值转死信并置 DEAD + ACK */
    private void handleFailure(MapRecord<String, Object, Object> record, SeckillOrderMessage message,
                               String messageId, SeckillMessageLog logEntity, Exception e) {
        log.error("消息处理失败，messageId={}, orderNo={}", messageId, message.getOrderNo(), e);
        int newRetry = (logEntity.getRetryCount() == null ? 0 : logEntity.getRetryCount()) + 1;
        logEntity.setRetryCount(newRetry);
        logEntity.setStatus(MessageLogStatusEnum.FAILED.getCode());
        logEntity.setErrorMessage(truncate(e.getMessage()));
        seckillMessageLogMapper.updateById(logEntity);

        if (newRetry >= MAX_RETRY) {
            // 超过最大重试：转死信、日志置 DEAD、ACK 原消息移出 PEL
            sendToDeadLetter(message, messageId, e.getMessage());
            logEntity.setStatus(MessageLogStatusEnum.DEAD.getCode());
            seckillMessageLogMapper.updateById(logEntity);
            ack(record);
            log.warn("消息进入死信流: orderNo={}, messageId={}", message.getOrderNo(), messageId);
            // R5：订单未落库即进死信（用户必然未抢到），补偿回收库存与幂等令牌，避免资源永久悬空；
            // 订单已落库的极端情况（落库成功但后续入榜失败）不补偿，避免误收回已成交的资源。
            if (message.getOrderNo() != null
                    && seckillOrderMapper.selectByOrderNo(message.getOrderNo()) == null) {
                compensateDeadMessage(message);
            }
        } else {
            // 未达阈值：不 ACK，消息留在 PEL，由 consumerRetry(0) 再次拾起
            log.info("消息保留在 PEL 等待重试，当前重试次数={}", newRetry);
        }
    }

    /**
     * 发送死信：明文 Map 投递到 seckill:order:dead:stream，携带原消息字段 + 失败原因，便于人工回放。
     * 字段全字符串，避免对象序列化歧义（沿用 Day 3 教训：objectBacked → Base64 字段值坑）。
     */
    private void sendToDeadLetter(SeckillOrderMessage message, String messageId, String errorMsg) {
        Map<String, String> deadBody = new HashMap<>();
        deadBody.put("originalMessageId", messageId);
        deadBody.put("activityId", String.valueOf(message.getActivityId()));
        deadBody.put("productId", String.valueOf(message.getProductId()));
        deadBody.put("userId", String.valueOf(message.getUserId()));
        deadBody.put("orderNo", String.valueOf(message.getOrderNo()));
        deadBody.put("seckillPrice", String.valueOf(message.getSeckillPrice()));
        deadBody.put("requestTime", String.valueOf(message.getRequestTime()));
        deadBody.put("errorMessage", truncate(errorMsg));
        deadBody.put("failedAt", LocalDateTime.now().toString());
        redisTemplate.opsForStream().add(
                StreamRecords.mapBacked(deadBody).withStreamKey(CacheKeyConstant.SECKILL_DEAD_STREAM));
        log.info("死信已发送: originalMessageId={}", messageId);
    }

    private void ack(MapRecord<String, Object, Object> record) {
        try {
            redisTemplate.opsForStream().acknowledge(GROUP_NAME, record);
            log.debug("消息ACK成功: {}", record.getId().getValue());
        } catch (Exception e) {
            log.error("ACK失败: {}", record.getId().getValue(), e);
        }
    }

    /** 错误信息截断到 1023 字符（匹配表字段长度），null 兜底 */
    private String truncate(String text) {
        if (text == null || text.isEmpty()) {
            return "unknown";
        }
        return text.length() <= MAX_ERROR_MSG_LEN ? text : text.substring(0, MAX_ERROR_MSG_LEN);
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

    /** Map 值宽松转 Long：坏字段（如脏数据 "abc"）返回 null，不抛异常 */
    private Long lenientLong(Object value) {
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException ex) {
            log.warn("字段非数字，置 null 处理: value={}", value);
            return null;
        }
    }

    /**
     * 死信补偿：消息确认无法建单（订单未落库）时，回补 Redis 库存并把该用户从
     * 幂等令牌中释放，保证"无单可查 + 令牌/库存不归还"的资源悬空问题得到收敛。
     * 幂等语义与 SeckillServiceImpl.compensateAfterSendFailure 保持一致。
     */
    private void compensateDeadMessage(SeckillOrderMessage message) {
        if (message.getActivityId() == null || message.getUserId() == null) {
            log.warn("死信消息缺失 activityId/userId，跳过库存补偿: orderNo={}", message.getOrderNo());
            return;
        }
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + message.getActivityId();
        String tokenKey = CacheKeyConstant.SECKILL_USER_PREFIX + message.getActivityId() + ":" + message.getUserId();
        try {
            redisTemplate.opsForValue().increment(stockKey, 1);
            redisTemplate.delete(tokenKey);
            log.warn("死信补偿成功：回补库存并释放令牌, activityId={}, userId={}",
                    message.getActivityId(), message.getUserId());
        } catch (Exception ce) {
            log.error("死信补偿失败，需人工核对, activityId={}, userId={}, stockKey={}, tokenKey={}",
                    message.getActivityId(), message.getUserId(), stockKey, tokenKey, ce);
        }
    }

    /** Map 值安全转 String */
    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }
}
