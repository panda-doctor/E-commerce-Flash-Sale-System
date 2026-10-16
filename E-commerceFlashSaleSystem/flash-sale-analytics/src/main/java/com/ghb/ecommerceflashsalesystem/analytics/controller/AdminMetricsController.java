package com.ghb.ecommerceflashsalesystem.analytics.controller;


import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.analytics.vo.ActivityMetricsVO;
import com.ghb.ecommerceflashsalesystem.analytics.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 活动实时指标**读**端点（接口 4.12）。
 *
 * <p><b>P4 Day 10 · Step B 第二半</b>：本类原先还带一个写端点
 * {@code POST /{activityId}/snapshot}（快照落库）—— 那个动作已按"文件位置表达归属"
 * 搬到 **order 应用**（{@code order.controller.AdminMetricsCaptureController}），
 * 因为快照表的权威库是 {@code flash_sale_order}。本类保留纯读端点：它被单体与 order
 * 两个宿主同时扫到都无害（只读 Redis 计数 + 调 order 服务取订单数）。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/seckill/activities")
public class AdminMetricsController {
    private final MetricsService metricsService;

    /**
     * 获取活动实时指标（接口 4.12）
     */
    @GetMapping("/{activityId}/metrics")
    public Result<ActivityMetricsVO> getMetrics(@PathVariable Long activityId) {
        ActivityMetricsVO activityMetricsVO = metricsService.collectMetrics(activityId);
        return Result.success(activityMetricsVO);
    }
}
