package com.ghb.ecommerceflashsalesystem.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_activity_snapshot")

public class SeckillActivitySnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Integer redisStock;

    private Long orderCount;

    private Long queuedMessageCount;

    private Long successCount;

    private Long duplicateRejectCount;

    private Long rateLimitRejectCount;

    private Long soldOutRejectCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime snapshotTime;
}
