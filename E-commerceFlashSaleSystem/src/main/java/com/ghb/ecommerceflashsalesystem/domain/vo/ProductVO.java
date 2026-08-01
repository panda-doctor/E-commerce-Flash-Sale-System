package com.ghb.ecommerceflashsalesystem.domain.vo;

import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import lombok.Data;

/**
 * 商品视图对象，用于接口返回，不包含敏感/冗余字段（如创建时间、更新时间）。
 */
@Data
public class ProductVO {
    /**
     * 商品编号
     */
    private Long productId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 商品图片地址
     */
    private String imageUrl;

    /**
     * 原价，单位：分
     */
    private Long originalPrice;

    /**
     * 商品总库存
     */
    private Integer totalStock;

    /**
     * 普通可用库存
     */
    private Integer availableStock;

    /**
     * 商品状态（上架/下架）
     */
    private ProductStatusEnum status;

    /**
     * 是否为缓存命中（用于监控缓存效果）
     */
    private boolean cacheHit = false;
}
