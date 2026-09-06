package com.ghb.ecommerceflashsalesystem.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.dto.message.SeckillOrderMessage;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillMessageLog;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.enums.MessageLogStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillMessageLogMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import com.ghb.ecommerceflashsalesystem.stream.consumer.SeckillOrderConsumer;
import com.ghb.ecommerceflashsalesystem.stream.producer.SeckillOrderStreamProducer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第 3 阶段 Day 4：消费可靠性测试
 *
 * 验证：
 * 1. 成功路径：seckill_message_log status=SUCCESS，orderNo 正确；
 * 2. 失败路径：消息处理失败 retry_count 递增、status=FAILED，未达阈值留在 PEL（可被 consumerRetry 重新拾起）；
 * 3. 死信流转：retry 达阈值后进入死信流、日志 status=DEAD、原消息 ACK。
 */
@Slf4j
@SpringBootTest
public class SeckillMessageReliabilityTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private SeckillMessageLogMapper seckillMessageLogMapper;

    @Autowired
    private SeckillOrderConsumer consumer;

    @Autowired
    private SeckillOrderStreamProducer producer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 成功路径：真实活动 300（product 1 种子商品），库存充足
    private static final Long ACTIVITY_ID = 300L;
    private static final Long USER_ID = 8888L;
    // 失败路径：DB 中不存在的活动 → 落库触发外键异常，用于制造消费失败
    private static final Long BAD_ACTIVITY_ID = 999999L;
    private static final int INITIAL_STOCK = 10;

    @BeforeEach
    void setUp() {
        cleanRedisKeys(CacheKeyConstant.SECKILL_ORDER_STREAM);
        cleanRedisKeys(CacheKeyConstant.SECKILL_DEAD_STREAM);
        cleanRedisKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        // 清理数据库测试数据（订单/日志/活动）
        seckillOrderMapper.delete(new LambdaQueryWrapper<SeckillOrder>()
                .in(SeckillOrder::getActivityId, Arrays.asList(ACTIVITY_ID, BAD_ACTIVITY_ID)));
        seckillMessageLogMapper.delete(new LambdaQueryWrapper<SeckillMessageLog>()
                .in(SeckillMessageLog::getActivityId, Arrays.asList(ACTIVITY_ID, BAD_ACTIVITY_ID)));
        seckillActivityMapper.deleteById(ACTIVITY_ID);

        // 插入真实活动并预热（外键约束：seckill_order.product_id 需引用存在的商品 1）
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(1L);
        activity.setActivityName("消费可靠性测试活动");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(INITIAL_STOCK);
        activity.setLimitPerUser(1);
        activity.setStatus(ActivityStatusEnum.RUNNING.getCode());
        activity.setPreheatStatus(0);
        activity.setVersion(0);
        seckillActivityMapper.insert(activity);
        seckillCacheService.preheatActivity(ACTIVITY_ID);

        consumer.ensureGroup();
    }

    @AfterEach
    void tearDown() {
        cleanRedisKeys(CacheKeyConstant.SECKILL_ORDER_STREAM);
        cleanRedisKeys(CacheKeyConstant.SECKILL_DEAD_STREAM);
        cleanRedisKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        seckillOrderMapper.delete(new LambdaQueryWrapper<SeckillOrder>()
                .in(SeckillOrder::getActivityId, Arrays.asList(ACTIVITY_ID, BAD_ACTIVITY_ID)));
        seckillMessageLogMapper.delete(new LambdaQueryWrapper<SeckillMessageLog>()
                .in(SeckillMessageLog::getActivityId, Arrays.asList(ACTIVITY_ID, BAD_ACTIVITY_ID)));
        seckillActivityMapper.deleteById(ACTIVITY_ID);
    }

    private void cleanRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private SeckillMessageLog findLog(String messageId) {
        return seckillMessageLogMapper.selectOne(new LambdaQueryWrapper<SeckillMessageLog>()
                .eq(SeckillMessageLog::getStreamMessageId, messageId));
    }

    private long deadStreamLen() {
        Long len = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.xLen(CacheKeyConstant.SECKILL_DEAD_STREAM.getBytes(StandardCharsets.UTF_8)));
        return len == null ? 0 : len;
    }

    // ---------- 用例1：成功路径 → 日志 SUCCESS ----------
    @Test
    void testSuccessLogStatus() {
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(ACTIVITY_ID);
        request.setUserId(USER_ID);
        SeckillResponse response = seckillService.execute(request);
        assertThat(response.getResult()).isEqualTo("QUEUED");
        String orderNo = response.getOrderNo();

        consumer.consumePending(10);

        SeckillMessageLog logEntity = seckillMessageLogMapper.selectOne(new LambdaQueryWrapper<SeckillMessageLog>()
                .eq(SeckillMessageLog::getOrderNo, orderNo));
        assertThat(logEntity).isNotNull();
        assertThat(logEntity.getStatus()).isEqualTo(MessageLogStatusEnum.SUCCESS.getCode());
        assertThat(logEntity.getRetryCount()).isEqualTo(0);
        // 订单确实落库
        assertThat(seckillOrderMapper.selectByOrderNo(orderNo)).isNotNull();
    }

    // ---------- 用例2：失败 → retry 递增留 PEL → 达阈值进死信 ----------
    @Test
    void testFailureRetryThenDeadLetter() {
        // 手工投递一条"落库必失败"的消息（活动 999999 不存在 → 外键异常）
        SeckillOrderMessage badMessage = SeckillOrderMessage.builder()
                .activityId(BAD_ACTIVITY_ID)
                .productId(1L)
                .userId(8889L)
                .orderNo("SKTESTFAIL0001")
                .seckillPrice(9900L)
                .requestTime(System.currentTimeMillis())
                .build();
        RecordId recordId = producer.sendMessage(badMessage);
        String messageId = recordId.getValue();
        assertThat(messageId).isNotBlank();

        // 1. 第一次消费：失败，retry=1、FAILED，消息未 ACK（留在 PEL）
        consumer.consumePending(10);
        SeckillMessageLog log1 = findLog(messageId);
        assertThat(log1).isNotNull();
        assertThat(log1.getStatus()).isEqualTo(MessageLogStatusEnum.FAILED.getCode());
        assertThat(log1.getRetryCount()).isEqualTo(1);
        assertThat(deadStreamLen()).isEqualTo(0L);

        // 2. 从 PEL 重试至阈值：consumerRetry 每次拾起同一条，retry 递增
        for (int i = 2; i <= CacheKeyConstant.MESSAGE_MAX_RETRY; i++) {
            consumer.consumerRetry(10);
            SeckillMessageLog cur = findLog(messageId);
            assertThat(cur).isNotNull();
            assertThat(cur.getRetryCount()).isEqualTo(i);
        }

        // 3. 达阈值 → 死信流转：日志 DEAD、死信流出现消息
        SeckillMessageLog finalLog = findLog(messageId);
        assertThat(finalLog.getStatus()).isEqualTo(MessageLogStatusEnum.DEAD.getCode());
        assertThat(finalLog.getRetryCount()).isEqualTo(CacheKeyConstant.MESSAGE_MAX_RETRY);
        assertThat(deadStreamLen()).isEqualTo(1L);
        // 死信里应带原始消息 ID（人工回放依据）
        assertThat(findLog(messageId).getErrorMessage()).isNotBlank();
    }
}
