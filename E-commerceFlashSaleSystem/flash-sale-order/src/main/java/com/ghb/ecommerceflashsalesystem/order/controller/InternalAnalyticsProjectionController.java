package com.ghb.ecommerceflashsalesystem.order.controller;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.order.service.ProjectionRebuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P5 Day 3：投影回填的内部端点（仅集群内可达，与 {@code /internal/orders} 同前缀）。
 *
 * <p>为什么放在 order 应用：回填要读 {@code flash_sale_order.seckill_order}（order 域的表），
 * 而投影写入器（{@code SeckillRankService}）在 order 的消费链路上本来就在用 —— 表与写入器同处一应用，
 * 序列化与幂等语义自然一致。</p>
 *
 * <p>用法（演示/漂移修复）：</p>
 * <pre>
 *   curl -X POST http://127.0.0.1:8087/internal/orders/activities/90173201268736/rebuild-projection
 *   → {"code":0,"data":{"activityId":..., "replayed":1}}
 * </pre>
 *
 * <p>幂等：可反复调用（{@code recordSuccess} 内部去重）。</p>
 */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalAnalyticsProjectionController {

    private final ProjectionRebuildService projectionRebuildService;

    @PostMapping("/activities/{activityId}/rebuild-projection")
    public Result<RebuildResult> rebuild(@PathVariable Long activityId) {
        int replayed = projectionRebuildService.rebuild(activityId);
        return Result.success(new RebuildResult(activityId, replayed));
    }

    /** 回填结果（条数 = 回放的订单数，去重后实际新增可能更少）。 */
    public record RebuildResult(Long activityId, int replayed) {
    }
}
