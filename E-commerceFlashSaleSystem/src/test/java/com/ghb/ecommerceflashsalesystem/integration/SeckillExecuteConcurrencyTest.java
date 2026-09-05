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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@Slf4j
@SpringBootTest

public class SeckillExecuteConcurrencyTest {

    private static final String STREAM_KEY = CacheKeyConstant.SECKILL_ORDER_STREAM;
    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @MockBean
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long ACTIVITY_ID = 100L;
    private static final Long USER_ID_BASE = 1000L;
    private static final int INITIAL_STOCK = 100;
    private static final int THREAD_COUNT = 200;

    @BeforeEach
    void setUp() {
        //清理Redis测试键
        cleanRedisKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanRedisKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanRedisKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");

        // @MockBean 默认在每个测试方法结束后自动重置 mock（MockReset.AFTER），无需手动 reset。
        // 【复盘】曾误写空壳 reset() 方法与 Awaitility.reset() 静态导入：
        //   - 空壳方法不做事，误导后来者；
        //   - Awaitility.reset() 是无参方法（重置轮询配置），与带参调用不匹配导致编译报错。
        //   正确做法就是此处不调用 reset，直接重新 when(...) 打桩即可。

        // 准备mock活动（status 故意用 NOT_STARTED：验证 Day 2 的时间窗设计——预热后即使 DB status
        // 不是 RUNNING，只要处于时间窗口内 execute 也能抢到，售罄由 Lua 兜底）
        SeckillActivity mockActivity = new SeckillActivity();
        mockActivity.setId(ACTIVITY_ID);
        mockActivity.setProductId(1L);
        mockActivity.setActivityName("并发测试活动");
        mockActivity.setStartTime(LocalDateTime.now().minusHours(1));
        mockActivity.setEndTime(LocalDateTime.now().plusHours(2));
        mockActivity.setSeckillPrice(9900L);
        mockActivity.setSeckillStock(INITIAL_STOCK);
        mockActivity.setLimitPerUser(1);
        mockActivity.setStatus(ActivityStatusEnum.NOT_STARTED.getCode());
        mockActivity.setPreheatStatus(0); // 未预热

        // stub selectById 返回该活动
        when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(mockActivity);

        // stub update 返回 1（预热更新状态用）
        when(seckillActivityMapper.update(any(), any())).thenReturn(1);

        // 执行预热，将活动信息及库存写入 Redis
        seckillCacheService.preheatActivity(ACTIVITY_ID);

        log.info("预热完成，库存={}", INITIAL_STOCK);
    }

    @AfterEach
    void tearDown() {
        //清理测试键
        cleanRedisKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanRedisKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanRedisKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        log.info("测试后清理 Redis 键");
    }

    private void cleanRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()){
            redisTemplate.delete(keys);
            redisTemplate.delete(STREAM_KEY);
        }
    }
    @Test
    void testConcurrentSeckillNoOversell() throws InterruptedException {
        // 并发执行 200 次秒杀，库存为 100
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicReference<Exception> unexpectedException = new AtomicReference<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            long userId = USER_ID_BASE + i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    SeckillRequest request = new SeckillRequest();
                    request.setActivityId(ACTIVITY_ID);
                    request.setUserId(userId);
                    // 可选 productId 不传

                    SeckillResponse response = seckillService.execute(request);
                    // 成功返回 QUEUED
                    if (response != null && "QUEUED".equals(response.getResult())) {
                        successCount.incrementAndGet();
                    } else {
                        // 理论上不会发生
                        unexpectedException.set(new IllegalStateException("Unexpected response: " + response));
                    }
                } catch (BusinessException e) {
                    if (e.getResultCode() == ResultCode.OUT_OF_STOCK) {
                        outOfStockCount.incrementAndGet();
                    } else {
                        unexpectedException.set(e);
                    }
                } catch (Exception e) {
                    unexpectedException.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 所有线程同时起跑
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        executorService.shutdown();

        //检查是否有意外情况
        if (unexpectedException.get() != null) {
            throw new RuntimeException("并发测试出现意外异常", unexpectedException.get());
        }

        // 断言成功数等于初始库存（100）
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        // 断言库存不足异常数 = 总请求 - 库存（200 - 100 = 100）
        assertThat(outOfStockCount.get()).isEqualTo(THREAD_COUNT - INITIAL_STOCK);
        // 断言 Redis 最终库存为 0
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        Object finalStock = redisTemplate.opsForValue().get(stockKey);
        assertThat(finalStock).isEqualTo(0);
        log.info("并发测试通过：成功{}次，库存不足{}次，最终库存{}",
                successCount.get(), outOfStockCount.get(), finalStock);
    }
}
