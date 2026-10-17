package com.ghb.ecommerceflashsalesystem.activity.scheduler;

import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.activity.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审查整改 P2-10：**周期性库存对账**（Redis 剩余库存 vs 权威配置 − 已成交数）。
 *
 * <p><b>为什么需要它</b>：现有的补偿是**事件驱动**的（消息转死信时回补库存，
 * 见 {@code SeckillOrderConsumer.compensateDeadMessage}）。它覆盖不了两类漂移：
 * ① 消息在进入死信流程前就丢失/人工误删 Redis 键；② 运维手工改过库存键。
 * 没有周期性对账，这类漂移只能等到"用户投诉买不到"才被发现。</p>
 *
 * <p><b>口径</b>：{@code 期望剩余 = 活动配置库存 − 已成交数}。
 * 已成交数取 **Redis 投影** {@code seckill:metric:<id>.orderCount}（P5 起由事件累加、与权威库对账过），
 * 因此本任务**不需要跨库 JOIN**（活动库只有配置库存，订单库才有订单），也不访问其它运行时。</p>
 *
 * <p><b>只读、只告警、不自动改写库存</b>：自动改写可能掩盖真实缺陷（例如重复扣减），
 * 所以这里只做"发现并放大"——差异写入 {@code seckill:stock:reconcile:diff}（活动数）并 WARN，
 * 由 Runbook 的人工/运维流程决定是否修复。</p>
 *
 * <p>调度：默认每天 04:30（{@code flash.stock.reconcile.cron} 可改）；
 * {@code flash.stock.reconcile.enabled=false} 可关闭。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconcileScheduler {

    private final SeckillActivityMapper seckillActivityMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${flash.stock.reconcile.enabled:true}")
    private boolean enabled;

    /** 对账入口（测试可直接调用，不依赖调度）。返回发现差异的活动数。 */
    public int reconcile() {
        if (!enabled) {
            log.debug("库存对账已关闭（flash.stock.reconcile.enabled=false）");
            return 0;
        }
        List<SeckillActivity> activities = seckillActivityMapper.selectList(null);
        int checked = 0;
        int diffs = 0;
        for (SeckillActivity activity : activities) {
            Long activityId = activity.getId();
            Object stockValue = redisTemplate.opsForValue().get(CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId);
            if (stockValue == null) {
                continue;   // 未预热/已过期：库存键不存在属正常，跳过
            }
            checked++;
            long redisStock = toLong(stockValue, -1L);
            long ordered = toLong(redisTemplate.opsForHash().get(
                    CacheKeyConstant.SECKILL_METRIC_PREFIX + activityId,
                    CacheKeyConstant.METRIC_FIELD_ORDER_COUNT), 0L);
            long configured = activity.getSeckillStock() == null ? 0L : activity.getSeckillStock();
            long expected = configured - ordered;
            if (redisStock != expected) {
                diffs++;
                log.warn("库存对账不一致：activityId={} 配置={} 已成交={} 期望剩余={} Redis剩余={} 差值={}",
                        activityId, configured, ordered, expected, redisStock, redisStock - expected);
            }
        }
        // 差异活动数写入 Redis 供监控采集（0 = 健康）
        redisTemplate.opsForValue().set("seckill:stock:reconcile:diff", String.valueOf(diffs));
        log.info("库存对账完成：活动总数={} 已预热={} 差异={}", activities.size(), checked, diffs);
        return diffs;
    }

    /** 每天 04:30 执行（低峰；cron 可配）。 */
    @Scheduled(cron = "${flash.stock.reconcile.cron:0 30 4 * * *}")
    public void scheduledReconcile() {
        try {
            reconcile();
        } catch (Exception e) {
            log.error("库存对账任务异常（本轮未完成）", e);
        }
    }

    private static long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
