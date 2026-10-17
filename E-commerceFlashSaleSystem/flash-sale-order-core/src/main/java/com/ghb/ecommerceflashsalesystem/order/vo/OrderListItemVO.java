package com.ghb.ecommerceflashsalesystem.order.vo;

import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.OrderStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审查整改 P2-6：订单列表项 VO。
 *
 * <p><b>为什么要它</b>：原实现用 {@code Map<String,Object>} 手工拼装响应，字段契约只存在于
 * 三行 `put` 里 —— 前端无法从类型看出结构，后端改字段也不会被编译器发现。改为 VO 后：
 * 字段名**保持不变**（与既有的 `orderNo/activityId/productId/status/createdAt` 完全一致，
 * 前端无需改动），但契约变成可编译、可被文档化的类型。</p>
 *
 * <p>{@code status} 仍是枚举名（`QUEUING/SUCCESS/FAILED/...`）：历史数据里若出现无法识别的
 * 码值，回退为 {@code QUEUING}（与原实现的兜底一致，避免前端拿到 null）。</p>
 */
@Data
public class OrderListItemVO {

    private String orderNo;
    private Long activityId;
    private Long productId;
    private String status;
    private LocalDateTime createdAt;

    public static OrderListItemVO of(SeckillOrder order) {
        OrderListItemVO vo = new OrderListItemVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setActivityId(order.getActivityId());
        vo.setProductId(order.getProductId());
        OrderStatusEnum status = OrderStatusEnum.fromValue(order.getStatus());
        vo.setStatus(status == null ? OrderStatusEnum.QUEUING.name() : status.name());
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }
}
