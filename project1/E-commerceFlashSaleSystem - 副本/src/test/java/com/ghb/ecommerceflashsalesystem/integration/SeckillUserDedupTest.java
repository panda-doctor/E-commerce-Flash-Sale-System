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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest(properties = "flash.stream.auto-poll=false")
public class SeckillUserDedupTest {
    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @MockBean
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String STREAM_KEY = CacheKeyConstant.SECKILL_ORDER_STREAM;
    private static final Long ACTIVITY_ID = 100L;
    private static final Long USER_A = 1001L;
    private static final Long USER_B = 1002L;
    private static final int INITIAL_STOCK = 10;  // 充足库存，用于测试幂等

    @BeforeEach
    void setUp() {
        // 清理所有测试键
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");

        // Mock 活动
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(1L);
        activity.setActivityName("幂等测试活动");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(INITIAL_STOCK);
        activity.setLimitPerUser(1);
        activity.setStatus(ActivityStatusEnum.RUNNING.getCode());
        activity.setPreheatStatus(0);

        //当被测代码调用这些 Mapper 方法时，不要真的去操作数据库，直接返回我指定的结果
        when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(seckillActivityMapper.update(any(), any())).thenReturn(1);

        // 预热缓存
        seckillCacheService.preheatActivity(ACTIVITY_ID);
    }

    @AfterEach
    void tearDown() {
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
    }

    private void cleanKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()){
            redisTemplate.delete(keys);
            redisTemplate.delete(STREAM_KEY);
        }
    }

    // ---------- 用例1：单用户防重 ----------
    @Test
    void testSingleUserDedup() {
        SeckillRequest requestOne = new SeckillRequest();
        requestOne.setActivityId(ACTIVITY_ID);
        requestOne.setUserId(USER_A);

        //第一次请求 成功
        SeckillResponse responseFirst = seckillService.execute(requestOne);
        assertThat(responseFirst.getResult()).isEqualTo("QUEUED");

        //第二次请求 抛 DUPLICATE_PURCHASE
        assertThatThrownBy(() -> seckillService.execute(requestOne))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->{
                    BusinessException businessException = (BusinessException) e;
                    assertThat(businessException.getResultCode())
                            .isEqualTo(ResultCode.DUPLICATE_PURCHASE);
                });
    }

    // ---------- 用例2：不同用户互不影响 ----------
    @Test
    void testDifferentUserIndependent() {
        SeckillRequest requestA = new SeckillRequest();
        requestA.setActivityId(ACTIVITY_ID);
        requestA.setUserId(USER_A);

        SeckillRequest requestB = new SeckillRequest();
        requestB.setActivityId(ACTIVITY_ID);
        requestB.setUserId(USER_B);

        SeckillResponse responseA = seckillService.execute(requestA);
        assertThat(responseA.getResult()).isEqualTo("QUEUED");

        SeckillResponse responseB = seckillService.execute(requestB);
        assertThat(responseB.getResult()).isEqualTo("QUEUED");

        //验证两个令牌键都存在
        String keyA = CacheKeyConstant.SECKILL_USER_PREFIX + ACTIVITY_ID + ":" + USER_A;
        String keyB = CacheKeyConstant.SECKILL_USER_PREFIX + ACTIVITY_ID + ":" + USER_B;
        assertThat(redisTemplate.hasKey(keyA)).isTrue();
        assertThat(redisTemplate.hasKey(keyB)).isTrue();
    }

    // ---------- 用例3：令牌 TTL 生效 ----------
    @Test
    void testTokenTTL() {
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(ACTIVITY_ID);
        request.setUserId(USER_A);

        seckillService.execute(request);

        String key = CacheKeyConstant.SECKILL_USER_PREFIX + ACTIVITY_ID + ":" + USER_A;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        // 刚建令牌 TTL 应接近 30 分钟：>0 且不超过常量值（曾误断言等于 0）
        assertThat(ttl).isGreaterThan(0L);
        assertThat(ttl).isLessThanOrEqualTo(CacheKeyConstant.SECKILL_USER_TOKEN_TTL);
    }

    // ---------- 用例4：售罄回滚令牌 ----------
    @Test
    void testTokenRollbackOnSoldOut() throws InterruptedException {
        // 先将库存耗光
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        redisTemplate.opsForValue().set(stockKey, 0);

        SeckillRequest request = new SeckillRequest();
        request.setActivityId(ACTIVITY_ID);
        request.setUserId(USER_A);

        // 此时请求应抛 OUT_OF_STOCK，且令牌键不应存在
        assertThatThrownBy(() -> seckillService.execute(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
            BusinessException be = (BusinessException) e;
            assertThat(be.getResultCode()).isEqualTo(ResultCode.OUT_OF_STOCK);
        });

        String key = CacheKeyConstant.SECKILL_USER_PREFIX + ACTIVITY_ID + ":" + USER_A;
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    // ---------- 用例5（加分）：同一用户并发，仅 1 次成功 ----------
    // 并发度取限流阈值：限流闸门在幂等之前，同用户并发若超过 RATE_LIMIT_MAX_COUNT，
    // 超额请求会被 42900 拦截（属限流语义而非幂等），因此取阈值个并发来验证幂等原子性。
    @Test
    void testConcurrentSameUser() throws InterruptedException {
        int threadCount = (int) CacheKeyConstant.RATE_LIMIT_MAX_COUNT;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicReference<Exception> unexpected = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    SeckillRequest request1 = new SeckillRequest();
                    request1.setActivityId(ACTIVITY_ID);
                    request1.setUserId(USER_A);
                    seckillService.execute(request1);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getResultCode() == ResultCode.DUPLICATE_PURCHASE) {
                        duplicateCount.incrementAndGet();
                    } else {
                        unexpected.set(e);
                    }
                } catch (Exception e) {
                    unexpected.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executor.shutdown();

        if (unexpected.get() != null) {
            throw new RuntimeException("并发异常", unexpected.get());
        }
        // 原子保证只有1次成功，其余49次为重复秒杀
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
    }
}
