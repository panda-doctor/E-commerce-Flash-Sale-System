package com.ghb.ecommerceflashsalesystem.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivitySnapshot;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.ActivityMetricsVO;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivitySnapshotMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.metrics.MetricsService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import com.ghb.ecommerceflashsalesystem.stream.consumer.SeckillOrderConsumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 第 4 阶段 Day 2：活动运行指标测试
 *
 * 验证：
 * 1. execute 三类拒绝（售罄 / 重复 / 限流）埋点计数正确；
 * 2. MetricsService.collectMetrics 聚合字段与 Redis / DB 实际状态一致（含"队列积压=XLEN-订单数"口径）；
 * 3. captureSnapshot 快照落库字段正确；
 * 4. 未预热空活动兜底返回 0/null，不抛异常。
 *
 * 【复盘】曾把 testSnapshotInsert / testEmptyActivityFallback 两个 @Test 方法
 * 嵌套进 testAggregationConsistency 的方法体（缺闭合大括号），编译报"非法表达式开始"；
 * 且限流用例误以为 limit_per_user=10 能让同一用户连抢 5 次成功——实际系统按令牌防重每人一单，
 * 第 2 次请求即命中 DUPLICATE。以下用例按真实业务重写。
 */
@Slf4j
@SpringBootTest(properties = "flash.stream.auto-poll=false")
public class SeckillMetricsTest {
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
    private MetricsService metricsService;

    @Autowired
    private SeckillActivitySnapshotMapper snapshotMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long ACTIVITY_ID = 600L;
    private static final Long USER_A = 30001L;
    private static final Long USER_B = 30002L;
    private static final Long USER_C = 30003L;

    @BeforeEach
    void setUp() {
        // 清理 Redis 键
        cleanKeys(CacheKeyConstant.SECKILL_METRIC_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_RANK_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_ORDER_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_DEAD_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        // 清理数据库
        snapshotMapper.delete(new LambdaQueryWrapper<SeckillActivitySnapshot>()
                .eq(SeckillActivitySnapshot::getActivityId, ACTIVITY_ID));
        seckillOrderMapper.delete(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
        seckillActivityMapper.deleteById(ACTIVITY_ID);
    }

    @AfterEach
    void tearDown() {
        setUp();
    }

    private void cleanKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private SeckillActivity createActivity(int stock, int limitPerUser) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(1L);
        activity.setActivityName("指标测试活动");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(stock);
        activity.setLimitPerUser(limitPerUser);
        activity.setStatus(ActivityStatusEnum.RUNNING.getCode());
        activity.setPreheatStatus(0);
        activity.setVersion(0);
        seckillActivityMapper.insert(activity);
        seckillCacheService.preheatActivity(ACTIVITY_ID);
        consumer.ensureGroup();
        return activity;
    }

    private SeckillResponse doSeckill(Long userId) {
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(ACTIVITY_ID);
        request.setUserId(userId);
        return seckillService.execute(request);
    }

    private BusinessException asBusinessException(Throwable e) {
        assertThat(e).isInstanceOf(BusinessException.class);
        return (BusinessException) e;
    }

    // ---------- 1. 售罄计数 ----------
    @Test
    void testSoldOutMetric() {
        // 库存 1：userA 抢走唯一库存后，userB 必然售罄
        createActivity(1, 1);
        SeckillResponse r1 = doSeckill(USER_A);
        assertThat(r1.getResult()).isEqualTo("QUEUED");
        consumer.consumePending(10); // 让订单落库（否则 XLEN-订单数会把这单算成积压）

        assertThatThrownBy(() -> doSeckill(USER_B))
                .satisfies(e -> {
                    BusinessException be = asBusinessException(e);
                    assertThat(be.getResultCode()).isEqualTo(ResultCode.OUT_OF_STOCK);
                });

        ActivityMetricsVO metrics = metricsService.collectMetrics(ACTIVITY_ID);
        assertThat(metrics.getSoldOutRejectCount()).isEqualTo(1);
        assertThat(metrics.getDuplicateRejectCount()).isEqualTo(0);
        assertThat(metrics.getRateLimitRejectCount()).isEqualTo(0);
        assertThat(metrics.getOrderCount()).isEqualTo(1);
        assertThat(metrics.getSuccessCount()).isEqualTo(1);
        assertThat(metrics.getRedisStock()).isEqualTo(0);
    }

    // ---------- 2. 重复计数 ----------
    @Test
    void testDuplicateMetric() {
        createActivity(10, 1);
        doSeckill(USER_A);
        consumer.consumePending(10);

        // userA 再次请求：令牌已建（DB 唯一键兜底），命中 -2 重复
        assertThatThrownBy(() -> doSeckill(USER_A))
                .satisfies(e -> {
                    BusinessException be = asBusinessException(e);
                    assertThat(be.getResultCode()).isEqualTo(ResultCode.DUPLICATE_PURCHASE);
                });

        ActivityMetricsVO metrics = metricsService.collectMetrics(ACTIVITY_ID);
        assertThat(metrics.getDuplicateRejectCount()).isEqualTo(1);
        assertThat(metrics.getOrderCount()).isEqualTo(1);
    }

    // ---------- 3. 限流计数 ----------
    @Test
    void testRateLimitMetric() {
        // 限流在第 0 步（早于任何业务校验），按 userId 60s/5 次计数：
        // 第 1 次请求 → 成功入队（建令牌）；
        // 第 2~5 次请求 → 通过限流后命中重复拒绝（-2）；
        // 第 6 次请求 → 限流窗口已满（第 0 步即拒）→ RATE_LIMITED。
        createActivity(100, 1);

        SeckillResponse first = doSeckill(USER_A);
        assertThat(first.getResult()).isEqualTo("QUEUED");

        // 第 2~5 次：重复拒绝 × 4
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> doSeckill(USER_A))
                    .satisfies(e -> {
                        BusinessException be = asBusinessException(e);
                        assertThat(be.getResultCode()).isEqualTo(ResultCode.DUPLICATE_PURCHASE);
                    });
        }
        // 第 6 次：限流拒绝
        assertThatThrownBy(() -> doSeckill(USER_A))
                .satisfies(e -> {
                    BusinessException be = asBusinessException(e);
                    assertThat(be.getResultCode()).isEqualTo(ResultCode.RATE_LIMITED);
                });

        ActivityMetricsVO metrics = metricsService.collectMetrics(ACTIVITY_ID);
        assertThat(metrics.getRateLimitRejectCount()).isEqualTo(1);
        assertThat(metrics.getDuplicateRejectCount()).isEqualTo(4);
        // 订单数 1（只有第 1 次成功入队；此时尚未消费不影响 DB 计数）
        assertThat(metrics.getOrderCount()).isEqualTo(0);
    }

    // ---------- 4. 聚合一致性 ----------
    @Test
    void testAggregationConsistency() {
        // 库存 2：userA、userB 各抢一单耗尽库存 → userC 售罄、userA 重复
        createActivity(2, 1);
        doSeckill(USER_A);
        doSeckill(USER_B);
        consumer.consumePending(10); // 两条消息落库

        assertThatThrownBy(() -> doSeckill(USER_C))
                .satisfies(e -> asBusinessException(e)); // OUT_OF_STOCK
        assertThatThrownBy(() -> doSeckill(USER_A))
                .satisfies(e -> {
                    BusinessException be = asBusinessException(e);
                    assertThat(be.getResultCode()).isEqualTo(ResultCode.DUPLICATE_PURCHASE);
                });

        ActivityMetricsVO metrics = metricsService.collectMetrics(ACTIVITY_ID);
        assertThat(metrics.getSoldOutRejectCount()).isEqualTo(1);
        assertThat(metrics.getDuplicateRejectCount()).isEqualTo(1);
        assertThat(metrics.getOrderCount()).isEqualTo(2);
        assertThat(metrics.getSuccessCount()).isEqualTo(2);
        assertThat(metrics.getRedisStock()).isEqualTo(0);

        // 队列口径验证：消费 ACK 后消息仍在 Stream（未 XDEL），XLEN=2；
        // "积压 = XLEN - 已落库订单数 = 0"，即无在途积压。
        Long streamLen = redisTemplate.execute((RedisCallback<Long>) conn -> conn.xLen(
                redisTemplate.getStringSerializer().serialize(CacheKeyConstant.SECKILL_ORDER_STREAM)));
        assertThat(streamLen).isEqualTo(2L);
        assertThat(metrics.getQueuedMessageCount()).isEqualTo(0L);
    }

    // ---------- 5. 快照落库 ----------
    @Test
    void testSnapshotInsert() {
        // 库存 2：userA、userB 成功耗尽库存，随后 3 个新用户全部售罄
        createActivity(2, 1);
        doSeckill(USER_A);
        doSeckill(USER_B);
        consumer.consumePending(10);

        for (int i = 0; i < 3; i++) {
            final long userId = 31000L + i;
            assertThatThrownBy(() -> doSeckill(userId))
                    .satisfies(e -> {
                        BusinessException be = asBusinessException(e);
                        assertThat(be.getResultCode()).isEqualTo(ResultCode.OUT_OF_STOCK);
                    });
        }

        metricsService.captureSnapshot(ACTIVITY_ID);

        List<SeckillActivitySnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<SeckillActivitySnapshot>()
                        .eq(SeckillActivitySnapshot::getActivityId, ACTIVITY_ID));
        assertThat(snapshots).hasSize(1);
        SeckillActivitySnapshot snap = snapshots.get(0);
        assertThat(snap.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(snap.getOrderCount()).isEqualTo(2); // 只有两个成功落库
        assertThat(snap.getSoldOutRejectCount()).isEqualTo(3); // 3 个用户售罄
        assertThat(snap.getDuplicateRejectCount()).isEqualTo(0);
        assertThat(snap.getRedisStock()).isEqualTo(0);
        assertThat(snap.getSnapshotTime()).isNotNull();
    }

    // ---------- 6. 空活动兜底 ----------
    @Test
    void testEmptyActivityFallback() {
        // 没有活动、没有预热、没有数据：聚合应返回空值/0，不抛异常
        ActivityMetricsVO metrics = metricsService.collectMetrics(ACTIVITY_ID);
        assertThat(metrics.getRedisStock()).isNull(); // 未预热
        assertThat(metrics.getOrderCount()).isEqualTo(0);
        assertThat(metrics.getQueuedMessageCount()).isEqualTo(0);
        assertThat(metrics.getRateLimitRejectCount()).isEqualTo(0);
        assertThat(metrics.getDuplicateRejectCount()).isEqualTo(0);
        assertThat(metrics.getSoldOutRejectCount()).isEqualTo(0);
    }
}
