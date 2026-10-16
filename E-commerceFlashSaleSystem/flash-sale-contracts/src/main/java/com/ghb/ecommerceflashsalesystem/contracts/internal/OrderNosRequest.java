package com.ghb.ecommerceflashsalesystem.contracts.internal;

import java.util.List;

/**
 * 榜单补订单号的内部请求（P4 Day 8）。
 *
 * <p>对应 {@code analytics.port.OrderQueryPort.orderNosOf(activityId, userIds)} —— 单体侧的
 * rank 端点把榜单里的 userId 集合送过来，order 服务回 {@code userId → orderNo}。</p>
 */
public record OrderNosRequest(Long activityId, List<Long> userIds) {
}
