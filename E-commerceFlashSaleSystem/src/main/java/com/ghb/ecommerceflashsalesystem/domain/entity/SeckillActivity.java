package com.ghb.ecommerceflashsalesystem.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("seckill_activity")
public class SeckillActivity {
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long productId;

    private String activityName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long seckillPrice;

    private Integer seckillStock;

    private Integer limitPerUser;

    private Integer status;

    private Integer preheatStatus;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
