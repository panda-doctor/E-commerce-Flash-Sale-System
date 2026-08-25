package com.ghb.ecommerceflashsalesystem.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动详情视图（对应接口 4.6）
 */
@Data
public class SeckillActivityVO {

    /**
     * 活动编号
     */
    private Long activityId;

    /**
     * 商品编号
     */
    private Long productId;

    /**
     * 活动名称
     */
    private String activityName;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /**
     * 活动状态（序列化输出枚举名称，如 "RUNNING"）
     */
    private ActivityStatusEnum status;

    /**
     * 秒杀价，单位：分（增强字段，非必须但有助于前端展示）
     */
    private Long seckillPrice;

    /**
     * 实时缓存库存（从 Redis 读取，非实体字段）
     */
    private Integer stock;

    /**
     * 每用户限购数量
     */
    private Integer limitPerUser;
}