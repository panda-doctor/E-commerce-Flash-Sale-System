package com.ghb.ecommerceflashsalesystem.order.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.analytics.port.OrderQueryPort;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.order.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单查询端口的 order 侧实现（P0 Day 4）。
 *
 * <p>订单数据的所有权在 order 域，analytics 只声明需要什么；装配期由 Spring 注入本实现，
 * 从而保持 order → analytics 单一依赖方向（无模块环）。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderQueryAdapter implements OrderQueryPort {

    private final SeckillOrderMapper seckillOrderMapper;

    @Override
    public Map<Long, String> orderNosOf(Long activityId, Collection<Long> userIds) {
        if (activityId == null || userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 与原榜单口径一致：同活动同用户唯一订单，按落库顺序排列
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getActivityId, activityId)
                .in(SeckillOrder::getUserId, userIds)
                .orderByAsc(SeckillOrder::getCreatedAt);
        List<SeckillOrder> orders = seckillOrderMapper.selectList(wrapper);
        return orders.stream().collect(Collectors.toMap(
                SeckillOrder::getUserId,
                SeckillOrder::getOrderNo,
                (first, ignore) -> first,
                LinkedHashMap::new));
    }

    @Override
    public long countByActivity(Long activityId) {
        if (activityId == null) {
            return 0L;
        }
        Long count = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, activityId));
        return count == null ? 0L : count;
    }
}
