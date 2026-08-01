package com.ghb.ecommerceflashsalesystem.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("seckill_order")
public class SeckillOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long activityId;

    private Long productId;

    private Long userId;

    /**
     * 成交价，单位：分
     */
    private Long seckillPrice;

    /**
     * 状态：0排队中，1已创建，2创建失败，3已取消
     */
    private Integer status;

    /**
     * 缓存消息编号
     */
    private String streamMessageId;

    /**
     * 失败原因
     */
    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
