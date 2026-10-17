package com.ghb.ecommerceflashsalesystem.analytics.health;

import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * P6 Day 2：analytics 读模型（CQRS 读侧）的健康探针。
 *
 * <p><b>放在单体而不是 analytics 库</b>：`flash-sale-analytics` 模块没有 actuator 依赖，
 * 而"读模型是否新鲜"恰恰是运维在 {@code /actuator/health} 上要看的东西。本类包名仍在
 * {@code .analytics}（被单体扫描），只依赖 Redis 与常量，不牵动其它模块。</p>
 *
 * <p><b>它回答什么问题</b>：投影是派生数据，写失败或消费停摆时"查询还能返回"但数据会悄悄变旧。
 * 这里把两个关键漂移信号暴露出来：</p>
 * <ul>
 *   <li>{@code projectionFailureTotal}：P5 引入的失败累计（>0 即 {@code DEGRADED}）；</li>
 *   <li>{@code deadLetterCount}：死信流长度（消费持续失败的直接证据）。</li>
 * </ul>
 *
 * <p><b>为什么不在这里做全量对账</b>：健康检查必须 O(1) 且不碰业务库；全量对账由
 * {@code scripts/reconcile-analytics-projection.ps1} 在发布/巡检时执行。</p>
 *
 * <p>状态：{@code UP} / {@code DEGRADED}（有写失败或死信）/ {@code UNKNOWN}（探测本身失败 ——
 * 既不谎报 UP，也不因此把整个应用判死）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectionHealthIndicator implements HealthIndicator {

    /** 自定义聚合状态：能返回数据但派生数据可能不新鲜（不触发应用下线）。 */
    public static final String STATUS_DEGRADED = "DEGRADED";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            Object raw = redisTemplate.opsForValue().get(CacheKeyConstant.SECKILL_PROJECTION_FAILURE_TOTAL);
            long failures = raw instanceof Number number ? number.longValue() : 0L;
            Long deadLetters = redisTemplate.opsForStream().size(CacheKeyConstant.SECKILL_DEAD_STREAM);
            long dead = deadLetters == null ? 0L : deadLetters;

            Health.Builder builder = (failures > 0 || dead > 0)
                    ? Health.status(STATUS_DEGRADED)
                    : Health.up();
            return builder
                    .withDetail("projectionFailureTotal", failures)
                    .withDetail("deadLetterCount", dead)
                    .build();
        } catch (Exception e) {
            log.warn("投影健康探针执行失败（Redis 可能不可用）", e);
            return Health.unknown().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
