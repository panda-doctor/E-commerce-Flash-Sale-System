package com.ghb.ecommerceflashsalesystem.order.health;

import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 审查整改 P2-5：**在消费方（order 应用）也暴露投影/死信探针**。
 *
 * <p>此前只有单体（bootstrap）暴露 {@code projection} 组件，而真正写投影、真正产生死信的正是
 * order 应用的消费者。order 单独部署时，运维在它的 {@code /actuator/health} 上更应该看到这两个信号。</p>
 *
 * <p>语义与单体版本一致：</p>
 * <ul>
 *   <li>{@code projectionFailureTotal > 0} 或 {@code deadLetterCount > 0} → {@code DEGRADED}；</li>
 *   <li>探测本身失败（Redis 不可用）→ {@code UNKNOWN}（不谎报 UP，也不把应用判死）。</li>
 * </ul>
 *
 * <p>注意：各自运行时的 bean 名相同（{@code projectionHealthIndicator}），但它们分属不同应用上下文，
 * 不冲突；单体内由 bootstrap 的同名类提供。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectionHealthIndicator implements HealthIndicator {

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
            log.warn("order 侧投影健康探针执行失败（Redis 可能不可用）", e);
            return Health.unknown().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
