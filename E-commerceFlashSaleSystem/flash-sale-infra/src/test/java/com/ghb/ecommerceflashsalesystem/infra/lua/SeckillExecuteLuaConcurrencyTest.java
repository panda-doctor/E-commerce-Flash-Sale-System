package com.ghb.ecommerceflashsalesystem.infra.lua;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审查整改 R1（P1-1）：**秒杀扣减脚本的并发正确性**。
 *
 * <p>为什么这样测：超卖只可能发生在"检查库存 → 扣减"这个窗口。`seckill_execute.lua`
 * 把 令牌检查 + 令牌创建 + 库存判断 + 扣减 放进同一脚本原子执行，所以只要在真实 Redis 上
 * 用**真实脚本 + 真实并发**压它，就能证伪/证明超卖。这里不启 Spring 上下文、不碰业务库，
 * 全部使用 **Redis db=15**（隔离库），用例结束即 flush，绝不污染演示数据（db=1）。</p>
 *
 * <p>脚本返回协议（`seckill_execute.lua`）：{@code >=0} 成功（剩余库存）、{@code -1} 售罄、
 * {@code -2} 重复秒杀。</p>
 */
class SeckillExecuteLuaConcurrencyTest {

    /** 隔离库：业务数据在 db=1，测试固定用 15。 */
    private static final int TEST_DB = 15;
    private static final long ACTIVITY_ID = 990_001L;

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private DefaultRedisScript<Long> script;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        config.setDatabase(TEST_DB);
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();

        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill_execute.lua"));
        script.setResultType(Long.class);

        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    private Long execute(long userId) {
        return redis.execute(script,
                List.of(stockKey(), tokenKey(userId)),
                "1", "1800");
    }

    private String stockKey() {
        return "seckill:stock:" + ACTIVITY_ID;
    }

    private String tokenKey(long userId) {
        return "seckill:user:" + ACTIVITY_ID + ":" + userId;
    }

    @Test
    @DisplayName("库存 5 + 50 个不同用户并发 → 恰好 5 单成功、库存归零不超卖")
    void concurrentDistinctUsersNeverOversell() throws Exception {
        int stock = 5;
        int concurrency = 50;
        redis.opsForValue().set(stockKey(), String.valueOf(stock));

        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        runConcurrently(concurrency, i -> {
            long userId = 800_000L + i;
            Long r = execute(userId);
            if (r != null && r >= 0) {
                success.incrementAndGet();
            } else if (r != null && r == -1L) {
                soldOut.incrementAndGet();
            } else {
                unexpected.incrementAndGet();
            }
        });

        assertThat(unexpected.get()).as("不应出现 -2/异常返回").isZero();
        assertThat(success.get()).as("成功数必须恰好等于库存（超卖即 >5，少卖即 <5）").isEqualTo(stock);
        assertThat(soldOut.get()).isEqualTo(concurrency - stock);

        int finalStock = Integer.parseInt(redis.opsForValue().get(stockKey()));
        assertThat(finalStock).as("最终库存必须为 0 且不为负").isZero();
        // 令牌（一人一单）应恰好 5 个：按确定性的 userId 集合核对（不在并发段收集，避免线程不安全）
        long tokens = java.util.stream.IntStream.range(0, concurrency)
                .filter(i -> Boolean.TRUE.equals(redis.hasKey(tokenKey(800_000L + i))))
                .count();
        assertThat(tokens).as("成功人数应等于库存（失败者令牌已回滚）").isEqualTo(stock);
    }

    @Test
    @DisplayName("同一用户 20 并发 → 只成功 1 次（一人一单）")
    void concurrentSameUserOnlyOneSuccess() throws Exception {
        int stock = 10;
        int concurrency = 20;
        redis.opsForValue().set(stockKey(), String.valueOf(stock));

        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicated = new AtomicInteger();

        runConcurrently(concurrency, i -> {
            Long r = execute(700_001L);
            if (r != null && r >= 0) {
                success.incrementAndGet();
            } else if (r != null && r == -2L) {
                duplicated.incrementAndGet();
            }
        });

        assertThat(success.get()).as("同一用户并发只应成功一次").isEqualTo(1);
        assertThat(duplicated.get()).isEqualTo(concurrency - 1);
        assertThat(Integer.parseInt(redis.opsForValue().get(stockKey())))
                .as("只扣减 1 件").isEqualTo(stock - 1);
    }

    /** 用栅栏让所有线程尽量同时开跑，最大化竞争窗口。 */
    private void runConcurrently(int threads, java.util.function.IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        action.accept(index);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("并发任务应在 30s 内完成").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
