package com.ghb.ecommerceflashsalesystem.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动创建/更新请求 DTO
 */
@Data
public class ActivityRequest {

    /**
     * 活动编号（有值=更新，无值=创建）
     */
    private Long activityId;

    /**
     * 关联商品编号（必填）
     */
    @NotNull(message = "商品编号不能为空")
    private Long productId;

    /**
     * 活动名称（必填）
     */
    @NotBlank(message = "活动名称不能为空")
    private String activityName;

    /**
     * 开始时间（必填），格式：yyyy-MM-dd HH:mm:ss
     */
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 结束时间（必填），格式：yyyy-MM-dd HH:mm:ss
     */
    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /**
     * 秒杀价（必填），单位：分
     */
    @NotNull(message = "秒杀价不能为空")
    @Min(value = 0, message = "秒杀价不能为负数")
    private Long seckillPrice;

    /**
     * 秒杀库存（必填）
     */
    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存必须大于 0")
    private Integer seckillStock;

    /**
     * 每用户限购数量（必填）
     * E2：秒杀为"一人一单"——订单表 uk_activity_user（user+activity）唯一键硬约束，
     * 本参数仅支持 1（传其他值会被服务层拒绝），避免出现"配置 >1 却不生效"的误导。
     */
    @NotNull(message = "限购数量不能为空")
    @Min(value = 1, message = "限购数量必须为 1")
    private Integer limitPerUser;
}
