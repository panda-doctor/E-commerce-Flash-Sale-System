package com.ghb.ecommerceflashsalesystem.integration;


import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
public class PreheatConcurrencyTest {
    @Autowired
    private SeckillCacheService seckillCacheService;

    @MockBean
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long ACTIVITY_ID = 400L;

    // 共享状态模拟（用于 mock 的 thenAnswer）
    private static volatile int preheatStatus = 0;
    private static volatile int updateCallCount = 0;

    @BeforeEach
    void setUp() {
        // 清理Redis
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_LOCK_PREFIX + "*");
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        // 重置状态
        preheatStatus = 0;
        updateCallCount = 0;

        // Mock selectById：根据preheatStatus状态返回不同活动
        when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenAnswer(invocation -> {
            SeckillActivity activity = new SeckillActivity();
            activity.setId(ACTIVITY_ID);
            activity.setProductId(1L);
            activity.setActivityName("预热测试活动");
            activity.setStartTime(LocalDateTime.now().minusHours(1));
            activity.setEndTime(LocalDateTime.now().plusHours(2));
            activity.setSeckillPrice(9900L);
            activity.setSeckillStock(100);
            activity.setLimitPerUser(1);
            activity.setStatus(ActivityStatusEnum.NOT_STARTED.getCode());
            activity.setPreheatStatus(preheatStatus);
            activity.setVersion(0);
            return activity;
        });

        // Mock update：更新preheatStatus为1，并计数
        when(seckillActivityMapper.update(any(), any())).thenAnswer(invocation -> {
            updateCallCount++;
            preheatStatus = 1;  // 模拟更新成功
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_LOCK_PREFIX + "*");
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");
    }

    private void cleanKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void testConcurrentPreheat() throws InterruptedException {
        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyPreheatedCount = new AtomicInteger(0);
        AtomicReference<Exception> unexpected = new AtomicReference<>();

        for (int i =0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    seckillCacheService.preheatActivity(ACTIVITY_ID);
                    successCount.incrementAndGet();
                }catch (BusinessException e) {
                    // preheatActivity 防重抛出的消息是 "活动预热成功！！！不要重复..."，据此识别"已被预热"拒绝
                    if (e.getResultCode() == ResultCode.PARAM_ERROR
                            && e.getMessage().contains("不要重复")) {
                        alreadyPreheatedCount.incrementAndGet();
                    } else {
                        unexpected.set(e);
                    }
                }catch (Exception e) {
                    unexpected.set(e);
                }finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executor.shutdown();

        if (unexpected.get() != null) {
            throw new RuntimeException("并发测试异常", unexpected.get());
        }
        // 断言：恰好1个成功，7个因已预热被拒绝
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyPreheatedCount.get()).isEqualTo(threadCount - 1);

        // 断言：update仅被调用1次
        verify(seckillActivityMapper, times(1)).update(any(), any());


        // 断言：Redis中活动缓存和库存存在
        String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID;
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        assertThat(redisTemplate.hasKey(activityKey)).isTrue();
        assertThat(redisTemplate.hasKey(stockKey)).isTrue();

        // 断言：锁键已释放
        String lockKey = CacheKeyConstant.SECKILL_LOCK_PREFIX + "preheat:" + ACTIVITY_ID;
        assertThat(redisTemplate.hasKey(lockKey)).isFalse();
    }
}
