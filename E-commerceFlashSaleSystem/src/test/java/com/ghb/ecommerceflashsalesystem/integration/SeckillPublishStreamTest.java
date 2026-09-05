package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 第 3 阶段 Day 2：execute 扣库存成功后发布消息到 Redis Stream（削峰）的验收测试
 *
 * 验证点：
 * 1. execute 成功返回 QUEUED 且 orderNo 非空（格式 SK 开头）；
 * 2. 成功后 Stream（seckill:order:stream）中恰有 1 条消息；
 * 3. 失败路径（重复秒杀 / 售罄）不会向 Stream 追加消息——消息只由"真正扣到库存"的请求触发。
 */
@Slf4j
@SpringBootTest
public class SeckillPublishStreamTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private SeckillActivityMapper seckillActivityMapper;

    private static final Long ACTIVITY_ID = 100L;
    private static final Long USER_OK = 8001L;      // 成功发布
    private static final Long USER_DUP = 8002L;     // 重复秒杀（不新增消息）
    private static final Long USER_SOLD = 8003L;    // 售罄（不产生消息）
    private static final int INITIAL_STOCK = 2;

    private String streamKey;

    @BeforeEach
    void setUp() {
        streamKey = CacheKeyConstant.SECKILL_ORDER_STREAM;
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");
        redisTemplate.delete(streamKey);

        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(1L);
        activity.setActivityName("Stream发布测试活动");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(INITIAL_STOCK);
        activity.setLimitPerUser(1);
        activity.setStatus(ActivityStatusEnum.RUNNING.getCode());
        activity.setPreheatStatus(0);

        when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(seckillActivityMapper.update(any(), any())).thenReturn(1);

        seckillCacheService.preheatActivity(ACTIVITY_ID);
    }

    @AfterEach
    void tearDown() {
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");
        redisTemplate.delete(streamKey);
    }

    private void cleanKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private long streamLen() {
        // RedisTemplate<String, Object> 的 opsForStream() 泛型较绕，直接用底层连接执行 XLEN
        Long len = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) conn ->
                conn.xLen(streamKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return len == null ? 0 : len;
    }

    // ---------- 用例1：成功 → 返回 orderNo 且 Stream 恰好 1 条 ----------
    @Test
    void testPublishOnSuccess() {
        SeckillResponse response = executeSeckill(USER_OK, ACTIVITY_ID);
        assertThat(response.getResult()).isEqualTo("QUEUED");
        assertThat(response.getOrderNo()).isNotEmpty();
        assertThat(response.getOrderNo()).startsWith("SK");

        // 恰好 1 条消息（一次成功只发一条）
        assertThat(streamLen()).isEqualTo(1L);
    }

    // ---------- 用例2：重复秒杀不会追加消息 ----------
    @Test
    void testNoPublishOnDuplicate() {
        executeSeckill(USER_OK, ACTIVITY_ID);
        assertThat(streamLen()).isEqualTo(1L);

        // 同用户再次 execute：被幂等拦截，Stream 不新增
        assertThatThrownBy(() -> executeSeckill(USER_OK, ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.DUPLICATE_PURCHASE));
        assertThat(streamLen()).isEqualTo(1L);
    }

    // ---------- 用例3：售罄不会产生消息 ----------
    @Test
    void testNoPublishOnSoldOut() {
        // 将库存清零后请求
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        redisTemplate.opsForValue().set(stockKey, 0);

        assertThatThrownBy(() -> executeSeckill(USER_SOLD, ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.OUT_OF_STOCK));

        // 消息只由真正扣到库存的请求触发，售罄不产生
        assertThat(streamLen()).isEqualTo(0L);
    }

    private SeckillResponse executeSeckill(Long userId, Long activityId) {
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(activityId);
        request.setUserId(userId);
        return seckillService.execute(request);
    }
}
