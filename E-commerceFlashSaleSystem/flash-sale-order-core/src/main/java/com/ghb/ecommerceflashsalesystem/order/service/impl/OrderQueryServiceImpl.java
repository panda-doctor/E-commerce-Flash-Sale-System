package com.ghb.ecommerceflashsalesystem.order.service.impl;

import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.order.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.order.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单查询服务实现（当前为 Mapper 透传，作为分层边界与后续扩展点）。
 */
@Service
@RequiredArgsConstructor
public class OrderQueryServiceImpl implements OrderQueryService {

    private final SeckillOrderMapper seckillOrderMapper;

    @Override
    public SeckillOrder findByOrderNo(String orderNo) {
        return seckillOrderMapper.selectByOrderNo(orderNo);
    }

    @Override
    public List<SeckillOrder> listByUserAndActivity(Long userId, Long activityId) {
        return seckillOrderMapper.selectByUserIdAndActivityId(userId, activityId);
    }
}
