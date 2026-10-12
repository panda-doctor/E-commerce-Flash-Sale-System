package com.ghb.ecommerceflashsalesystem.domain.vo;

import lombok.Data;

@Data
public class ActivityMetricsVO {
    private Long activityId;
    private Integer redisStock;          // 可空
    private Long orderCount;
    private Long queuedMessageCount;
    private Long successCount;
    private Long duplicateRejectCount;
    private Long rateLimitRejectCount;
    private Long soldOutRejectCount;
}
