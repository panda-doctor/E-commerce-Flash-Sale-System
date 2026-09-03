package com.ghb.ecommerceflashsalesystem.domain.dto.response;

import lombok.Data;

@Data
public class SeckillResponse {
    /**
     * 活动编号
     */
    private Long activityId;

    /**
     * 用户编号
     */
    private Long userId;

    /**
     * 秒杀结果状态
     */
    private String result;


}
