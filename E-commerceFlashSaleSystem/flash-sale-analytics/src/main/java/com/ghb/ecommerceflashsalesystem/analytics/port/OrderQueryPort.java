package com.ghb.ecommerceflashsalesystem.analytics.port;

import java.util.Collection;
import java.util.Map;

/**
 * 订单查询端口（依赖倒置，P0 Day 4 引入）。
 *
 * <p>读模型（榜单 / 指标）需要订单数据，但 {@code order} 域又依赖 analytics 写入榜单，
 * 若 analytic 直接注入 {@code order.mapper.SeckillOrderMapper} 会形成模块环（ArchUnit R6）。
 * 因此由 analytics 定义所需能力、order 提供实现（{@code order.adapter.OrderQueryAdapter}）。</p>
 *
 * <p>P5 里程碑：改为订阅 {@code order.created} 事件后，本端口与适配器将被事件消费取代。</p>
 */
public interface OrderQueryPort {

    /**
     * 查询指定活动下这些用户的订单号映射（userId → orderNo）。
     *
     * @param activityId 活动 ID
     * @param userIds    榜单中的用户集合
     * @return userId → orderNo；无订单的用户不在结果中
     */
    Map<Long, String> orderNosOf(Long activityId, Collection<Long> userIds);

    /**
     * 统计指定活动已落库的订单数。
     */
    long countByActivity(Long activityId);
}
