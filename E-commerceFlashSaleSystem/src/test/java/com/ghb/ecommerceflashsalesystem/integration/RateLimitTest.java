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
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 第 3 阶段 Day 1 滑动窗口限流测试
 *
 * 设计说明：限流闸门位于 execute 最前置，而秒杀幂等（同用户同活动限购 1）在其之后，
 * 因此【不能】用"同一用户连续多次 execute 均成功"来测限流放行——第 2 次起会被幂等拦成 40901。
 * 正确姿势：
 *  - 放行/拒绝/滑动窗口语义：直接调用 rateLimitScript（脚本级，解耦业务）；
 *  - service 级：预填充限流键到阈值，验证 42900 已接入且最先于活动校验/幂等。
 */
@Slf4j
@SpringBootTest
public class RateLimitTest {


    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisScript<Long> rateLimitScript;

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @MockBean
    private SeckillActivityMapper seckillActivityMapper;

    // 限流/幂等互不干扰的测试用户（避开其它测试类使用的 1000~2000 段）
    private static final Long USER_CLEAN = 7001L;    // 干净用户（service 正常秒杀）
    private static final Long USER_FULL = 7002L;     // 预填满限流的用户（service 被 42900）
    private static final Long USER_NONEXIST = 7003L; // 预填满 + 不存在的活动（验证限流最先）

    private static final String STREAM_KEY = CacheKeyConstant.SECKILL_ORDER_STREAM;
    private static final Long ACTIVITY_ID = 100L;
    private static final int INITIAL_STOCK = 100;

    @BeforeEach
    void setUp() {
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        // Mock 一个进行中的活动并预热（service 级用例用）
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(1L);
        activity.setActivityName("限流测试活动");
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
        
    }

    private void cleanKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            redisTemplate.delete(STREAM_KEY);
        }
    }

    /** 向限流键填充 count 个指定 score 的成员（预填用） */
    private void fillRateKey(String rateKey, int count, double score) {
        for (int i = 0; i < count; i++) {
            redisTemplate.opsForZSet().add(rateKey, score + "-fill-" + i, score);
        }
    }

    private String uniqueMember(long now) {
        return now + "-" + System.nanoTime() + "-" + ThreadLocalRandom.current().nextInt(100000);
    }

    // ==================== 脚本级：放行 / 拒绝语义 ====================

    /**
     * 窗口内前 RATE_LIMIT_MAX_COUNT 次放行（返回 1），第 6 次拒绝（返回 0），窗口内成员数恰为阈值
     */
    @Test
    void testScriptAllowUntilLimit() {
        String rateKey = CacheKeyConstant.RATE_LIMIT_PREFIX + USER_CLEAN;
        long now = System.currentTimeMillis() / 1000;

        for (int i = 1; i <= CacheKeyConstant.RATE_LIMIT_MAX_COUNT; i++) {
            Long r = redisTemplate.execute(rateLimitScript,
                    Arrays.asList(rateKey),
                    now,
                    CacheKeyConstant.RATE_LIMIT_WINDOW_SECONDS,
                    CacheKeyConstant.RATE_LIMIT_MAX_COUNT,
                    uniqueMember(now));
            assertThat(r).isEqualTo(1L);
        }

        // 第 6 次应被拒绝
        Long rejected = redisTemplate.execute(rateLimitScript,
                Arrays.asList(rateKey),
                now,
                CacheKeyConstant.RATE_LIMIT_WINDOW_SECONDS,
                CacheKeyConstant.RATE_LIMIT_MAX_COUNT,
                uniqueMember(now));
        assertThat(rejected).isEqualTo(0L);

        // 窗口内成员数恰为阈值（拒绝的那次没有写入）
        assertThat(redisTemplate.opsForZSet().zCard(rateKey)).isEqualTo(CacheKeyConstant.RATE_LIMIT_MAX_COUNT);
    }

    // ==================== 脚本级：滑动窗口（真实滑动，非删键模拟） ====================

    /**
     * 预填 5 个"10 秒前"的旧成员，用 window=5 秒执行脚本：旧成员（now-10 < now-5）被清理，
     * 计数归 0 → 放行返回 1，窗口内只剩本次请求 1 个成员。验证的是 ZREMRANGEBYSCORE 的真实滑动清理。
     */
    @Test
    void testScriptWindowSliding() {
        String rateKey = CacheKeyConstant.RATE_LIMIT_PREFIX + USER_CLEAN;
        long now = System.currentTimeMillis() / 1000;
        int window = 5;

        // 5 个成员都在窗口外（10 秒前）
        fillRateKey(rateKey, (int) CacheKeyConstant.RATE_LIMIT_MAX_COUNT, now - 10);

        Long r = redisTemplate.execute(rateLimitScript,
                Arrays.asList(rateKey),
                now,
                (long) window,
                CacheKeyConstant.RATE_LIMIT_MAX_COUNT,
                uniqueMember(now));
        assertThat(r).isEqualTo(1L);

        // 旧成员被清掉，只留下本次放行写入的 1 个
        assertThat(redisTemplate.opsForZSet().zCard(rateKey)).isEqualTo(1L);
    }

    // ==================== service 级：限流已接入 execute 且最先 ====================

    /**
     * 预填满限流键后调 execute：应被 42900 拒绝；
     * 且对【不存在的活动】同样先返回 42900 而非 NOT_FOUND——证明限流闸门在活动校验之前。
     */
    @Test
    void testServiceRateLimitedFirst() {
        // 1. 正常活动 + 满限流 → RATE_LIMITED
        fillRateKey(CacheKeyConstant.RATE_LIMIT_PREFIX + USER_FULL,
                (int) CacheKeyConstant.RATE_LIMIT_MAX_COUNT, System.currentTimeMillis() / 1000.0);
        assertThatThrownBy(() -> executeSeckill(USER_FULL, ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.RATE_LIMITED));

        // 2. 不存在的活动 + 满限流 → 仍是 RATE_LIMITED（而不是 NOT_FOUND），限流最先
        fillRateKey(CacheKeyConstant.RATE_LIMIT_PREFIX + USER_NONEXIST,
                (int) CacheKeyConstant.RATE_LIMIT_MAX_COUNT, System.currentTimeMillis() / 1000.0);
        assertThatThrownBy(() -> executeSeckill(USER_NONEXIST, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.RATE_LIMITED));
    }

    // ==================== service 级：不同用户限流隔离 ====================

    /**
     * 干净用户 execute 正常 QUEUED；同一活动下预填满的用户被 42900——限流按 userId 隔离。
     */
    @Test
    void testServiceDifferentUsersIsolated() {
        // 干净用户：正常秒杀一次成功（库存充足）
        SeckillResponse response = executeSeckill(USER_CLEAN, ACTIVITY_ID);
        assertThat(response.getResult()).isEqualTo("QUEUED");

        // 另一用户预填满限流：同活动被 42900
        fillRateKey(CacheKeyConstant.RATE_LIMIT_PREFIX + USER_FULL,
                (int) CacheKeyConstant.RATE_LIMIT_MAX_COUNT, System.currentTimeMillis() / 1000.0);
        assertThatThrownBy(() -> executeSeckill(USER_FULL, ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.RATE_LIMITED));
    }

    private SeckillResponse executeSeckill(Long userId, Long activityId) {
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(activityId);
        request.setUserId(userId);
        return seckillService.execute(request);
    }
}
