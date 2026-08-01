package com.ghb.ecommerceflashsalesystem.domain.dto.request;

import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品创建/更新请求 DTO
 */
@Data
public class ProductRequest {
    /**
     * 商品编号（可选，有值=更新，无值=创建）
     */
    private Long productId;

    /**
     * 商品名称（必填）
     */
    @NotBlank(message = "商品名称不能为空")
    private String name;

    /**
     * 商品描述（可选）
     */
    private String description;

    /**
     * 商品图片地址（可选）
     */
    private String imageUrl;

    /**
     * 原价，单位：分（必填）
     */
    @NotNull(message = "原价不能为空")
    private Long originalPrice;

    /**
     * 商品总库存（必填）
     */
    @NotNull(message = "总库存不能为空")
    private Integer totalStock;

    /**
     * 商品状态（必填），如 "ON_SHELF" 或 "OFF_SHELF"
     */
    @NotNull(message = "商品状态不能为空")
    private ProductStatusEnum status;
}
