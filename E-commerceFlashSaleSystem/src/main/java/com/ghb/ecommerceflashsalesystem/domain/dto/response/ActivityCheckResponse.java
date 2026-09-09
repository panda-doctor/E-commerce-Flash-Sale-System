package com.ghb.ecommerceflashsalesystem.domain.dto.response;

import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import lombok.Data;

/**
 * 活动校验响应（对应接口 4.7）
 */
@Data
public class ActivityCheckResponse {

    /**
     * 活动编号
     */
    private Long activityId;

    /**
     * 用户编号
     */
    private Long userId;

    /**
     * 是否允许参与
     */
    private Boolean canJoin;

    /**
     * 活动状态
     */
    private ActivityStatusEnum activityStatus;

    /**
     * 原因说明，取值：ALLOW / ACTIVITY_NOT_PREHEATED / ACTIVITY_NOT_STARTED /
     * ACTIVITY_ENDED / ACTIVITY_SOLD_OUT / ACTIVITY_CANCELLED
     */
    private String reason;
}