package com.ghb.ecommerceflashsalesystem.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.RankEntryVO;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.rank.SeckillRankService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import com.ghb.ecommerceflashsalesystem.stream.consumer.SeckillOrderConsumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@Slf4j
@SpringBootTest
public class SeckillRankTest {
    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private SeckillOrderConsumer consumer;

    @Autowired
    private SeckillRankService rankService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long ACTIVITY_ID = 500L;
    private static final Long USER_A = 10001L;
    private static final Long USER_B = 10002L;
    private static final Long USER_C = 10003L;
    private static final Long USER_D = 10004L;

    @BeforeEach
    void setUp() {
        // 清理 Redis 键
        cleanKeys(CacheKeyConstant.SECKILL_RANK_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_ORDER_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_DEAD_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        // 清理数据库
        seckillOrderMapper.delete(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
        seckillActivityMapper.deleteById(ACTIVITY_ID);

        // 插入真实活动
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(1L);
        activity.setActivityName("榜单测试活动");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(100);
        activity.setLimitPerUser(1);
        activity.setStatus(ActivityStatusEnum.RUNNING.getCode());
        activity.setPreheatStatus(0);
        activity.setVersion(0);
        seckillActivityMapper.insert(activity);

        // 预热缓存
        seckillCacheService.preheatActivity(ACTIVITY_ID);
        // 确保消费者组存在
        consumer.ensureGroup();
    }

    @AfterEach
    void tearDown() {
        cleanKeys(CacheKeyConstant.SECKILL_RANK_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_ORDER_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_DEAD_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        seckillOrderMapper.delete(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
        seckillActivityMapper.deleteById(ACTIVITY_ID);
    }

    private void cleanKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    private SeckillResponse doSeckill(Long userId) {
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(ACTIVITY_ID);
        request.setUserId(userId);
        return seckillService.execute(request);
    }
    // 测试1：全链上榜 + 先后序
    @Test
    void testRankOrder() throws InterruptedException {
        // 三个用户依次秒杀（模拟时间先后，通过睡眠拉开差距）
        SeckillResponse r1 = doSeckill(USER_A);
        Thread.sleep(10);
        SeckillResponse r2 = doSeckill(USER_B);
        Thread.sleep(10);
        SeckillResponse r3 = doSeckill(USER_C);

        //消费所有信息
        consumer.consumePending(10);

        //查询榜单TOP3
        List<RankEntryVO> list = rankService.topN(ACTIVITY_ID, 3);
        assertThat(list).hasSize(3);
        // 第一名应为 USER_A（最早），第二名 USER_B，第三名 USER_C
        assertThat(list.get(0).getUserId()).isEqualTo(USER_A);
        assertThat(list.get(1).getUserId()).isEqualTo(USER_B);
        assertThat(list.get(2).getUserId()).isEqualTo(USER_C);

        // 验证分数递增
        assertThat(list.get(0).getScore()).isLessThan(list.get(1).getScore());
        assertThat(list.get(1).getScore()).isLessThan(list.get(2).getScore());

        // 验证 orderNo 非空
        Long count = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, ACTIVITY_ID)
        );
        assertThat(count).isEqualTo(3);
    }

    // 测试2：幂等/重放不重分
    @Test
    void testIdempotentRank() {
        doSeckill(USER_A);
        consumer.consumePending(10);

        //第一次查榜
        List<RankEntryVO> list1 = rankService.topN(ACTIVITY_ID, 10);
        assertThat(list1).hasSize(1);

        //再次消费（无新消息）
        consumer.consumePending(10);
        List<RankEntryVO> list2 = rankService.topN(ACTIVITY_ID, 10);
        assertThat(list2).hasSize(1);
        assertThat(list2.get(0).getUserId()).isEqualTo(USER_A);
        // 分数不变
        assertThat(list2.get(0).getScore()).isEqualTo(list1.get(0).getScore());
    }

    // 测试3：防重回归（同用户重复秒杀被拦截，榜不变）
    @Test
    void testDuplicateRejected() {
        doSeckill(USER_A);
        consumer.consumePending(10);
        List<RankEntryVO> list1 = rankService.topN(ACTIVITY_ID, 10);
        assertThat(list1).hasSize(1);

        // 再次执行秒杀，应抛出 40901
        assertThatThrownBy(() -> doSeckill(USER_A))
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getResultCode()).isEqualTo(ResultCode.DUPLICATE_PURCHASE);
                });

        // 榜单不变
        List<RankEntryVO> list2 = rankService.topN(ACTIVITY_ID, 10);
        assertThat(list2).hasSize(1);
        assertThat(list2.get(0).getUserId()).isEqualTo(USER_A);
    }

    // 测试4：并发恰好一次（多用户并发秒杀，最终榜人数=成功订单数，无重复member）
    @Test
    void testConcurrentOnce () throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicReference<Exception> unexpected = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            long userId = 20000L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    doSeckill(userId);
                }catch (Exception e) {
                    unexpected.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        if (unexpected.get() != null) {
            throw new RuntimeException("并发异常", unexpected.get());
        }
        // 消费所有消息
        consumer.consumePending(20);

        // 查榜
        List<RankEntryVO> list = rankService.topN(ACTIVITY_ID, 10);

        // 由于库存充足，10个用户都应成功，榜单应有10人
        assertThat(list).hasSize(10);

        // 验证无重复 userId
        long distinctCount = list.stream().map(RankEntryVO::getUserId).distinct().count();
        assertThat(distinctCount).isEqualTo(10);
        // 验证 DB 订单数
        Long orderCount = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, ACTIVITY_ID));
        assertThat(orderCount).isEqualTo(10);

    }
    // 测试5： 空榜返回空
    @Test
    void testEmptyRank() {
        List<RankEntryVO> list = rankService.topN(ACTIVITY_ID, 10);
        assertThat(list).isEmpty();
    }
}
