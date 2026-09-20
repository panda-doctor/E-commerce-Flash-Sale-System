package com.ghb.ecommerceflashsalesystem.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@SpringBootTest(properties = "flash.stream.auto-poll=false")
public class LuaStockDeductionTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisScript<Long> decrStockScript;

    private static final String STOCK_KEY_PREFIX = CacheKeyConstant.SECKILL_STOCK_PREFIX;
    private static final Long ACTIVITY_ID = 100L;
    private String stockKey;

    @BeforeEach
    void setUp() {
        // 清除所有 seckill : stock; * 测试键
        Set<String> keys = redisTemplate.keys(STOCK_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()){
            redisTemplate.delete(keys);
            log.debug("清理库存键: {}", keys);
        }
        stockKey = STOCK_KEY_PREFIX + ACTIVITY_ID;
    }

    @AfterEach
    void tearDown() {
        //清理测试键
        redisTemplate.delete(stockKey);
        log.debug("测试后清理库存键: {}", stockKey);
    }

    // ---------- 场景 1：正常扣减 ----------
    @Test
    void testNormalDeduction() {
        // 预设库存
        redisTemplate.opsForValue().set(stockKey, 100);
        Long result =  redisTemplate.execute(decrStockScript, Arrays.asList(stockKey),1);
        assertThat(result).isEqualTo(99L);

        Object stock = redisTemplate.opsForValue().get(stockKey);
        assertThat(stock).isEqualTo(99);
    }
    // ---------- 场景 2：库存为 0 ----------
    @Test
    void testZeroStock () {
        redisTemplate.opsForValue().set(stockKey, 0);
        Long result = redisTemplate.execute(decrStockScript, java.util.Arrays.asList(stockKey), 1);
        assertThat(result).isEqualTo(-1L);

        Object stock = redisTemplate.opsForValue().get(stockKey);
        assertThat(stock).isEqualTo(0);
    }
    // ---------- 场景 3：键不存在 ----------
    @Test
    void testKeyNotExists() {
        Long result = redisTemplate.execute(decrStockScript, Arrays.asList(stockKey), 1);
        assertThat(result).isEqualTo(-1L);
    }

    // ---------- 场景 4：并发防超卖（50 库存，10 线程各扣 1） ----------
    @Test
    void testConcurrentDeduction() throws InterruptedException {
        int initialStock = 50;
        int threadCount = 10;

        // 设置初始库存
        redisTemplate.opsForValue().set(stockKey, initialStock);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);  // 记录被拒绝的请求（可选）
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // 每个线程持续抢购，直到库存耗尽
                    while (true) {
                        Long result = redisTemplate.execute(
                                decrStockScript,
                                Arrays.asList(stockKey),
                                1  // 每次扣减1
                        );
                        if (result != null && result >= 0) {
                            successCount.incrementAndGet();
                        } else {
                            // 返回 -1 表示库存不足，退出循环
                            failCount.incrementAndGet();
                            break;
                        }
                    }
                } catch (Exception e) {
                    exceptionRef.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        if (exceptionRef.get() != null) {
            throw new RuntimeException("并发测试异常", exceptionRef.get());
        }

        executor.shutdown();

        // 验证最终库存为 0
        Object finalStock = redisTemplate.opsForValue().get(stockKey);
        assertThat(finalStock).isEqualTo(0);

        // 验证成功扣减次数恰好等于初始库存（50）
        assertThat(successCount.get()).isEqualTo(initialStock);

        // 可选：验证失败请求数（被拦截的超额请求）
        log.info("并发测试完成，成功扣减: {}, 失败拦截: {}", successCount.get(), failCount.get());
    }
}
