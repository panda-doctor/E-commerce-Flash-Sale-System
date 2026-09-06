package com.ghb.ecommerceflashsalesystem.domain.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_message_log")
public class SeckillMessageLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String streamKey;

    private String streamMessageId;

    private String consumerGroup;

    private String consumerName;

    private String orderNo;

    private Long activityId;

    private Long productId;

    private Long userId;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer retryCount;

    private Integer status; // 0待消费 1成功 2失败 3死信

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
