package com.ghb.ecommerceflashsalesystem.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.INPUT)
    private Long id;

    private String name;

    private String description;

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
     * 状态：0下架，1上架
     */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
