package com.ghb.ecommerceflashsalesystem.controller.admin;


import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.domain.vo.ActivityMetricsVO;
import com.ghb.ecommerceflashsalesystem.service.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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


    @PostMapping("/{activityId}/snapshot")
    public Result<Void> captureSnapshot(@PathVariable Long activityId) {
        metricsService.captureSnapshot(activityId);
        return Result.success();
    }
}
