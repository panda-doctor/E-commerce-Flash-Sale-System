package com.ghb.ecommerceflashsalesystem.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动列表条目（电商"活动广场"列表页使用）
 * 附带商品信息快照（名称/原价/图），避免前端逐条再查商品接口
 */
@Data
public class ActivityItemVO {

    private Long activityId;

    private Long productId;

    private String productName;

    private String productImage;

    private String activityName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /**
     * 秒杀价，单位：分
     */
    private Long seckillPrice;

    /**
     * 原价，单位：分（展示划线价）
     */
    private Long originalPrice;

    /**
     * 状态（序列化枚举名，与详情口径一致）
     */
    private ActivityStatusEnum status;

    /**
     * 实时库存：预热后取 Redis，未预热回退 DB 配置库存
     */
    private Integer stock;

    /**
     * 是否已预热（seckill:stock 键是否存在）。
     * M8：未预热时前端不得把活动当"可抢购"引导，应提示"库存未预热"；
     * 兼容旧数据（null 视为已预热）由前端处理。
     */
    private Boolean preheated;

    /**
     * 活动配置总库存（DB seckill_stock，用于计算"已抢百分比/已抢件数"）
     */
    private Integer totalStock;
}
