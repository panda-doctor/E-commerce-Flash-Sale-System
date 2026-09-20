package com.ghb.ecommerceflashsalesystem.service.metrics;

import com.ghb.ecommerceflashsalesystem.domain.vo.ActivityMetricsVO;

public interface MetricsService {
    /**
     * 实时聚合活动指标
     */
    ActivityMetricsVO collectMetrics (Long activity);

    /**
     * 将当前指标落库为快照
     */
    void captureSnapshot(Long activityId);
}
