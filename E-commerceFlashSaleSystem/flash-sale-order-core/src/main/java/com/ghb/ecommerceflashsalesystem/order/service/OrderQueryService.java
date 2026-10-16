package com.ghb.ecommerceflashsalesystem.order.service;

import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;

import java.util.List;

/**
 * 订单查询服务（P0 Day 4 引入）。
 *
 * <p>分层收敛：控制器原先直接注入 {@code SeckillOrderMapper}，违反"controller 不得依赖 mapper"
 * 的架构规则（ArchUnit R4）；本接口作为应用层入口，后续订单查询语义扩展（脱敏、分页、缓存）在此收口。</p>
 */
public interface OrderQueryService {

    /** 按订单号查询（不存在返回 null，调用方据此返回"排队中"） */
    SeckillOrder findByOrderNo(String orderNo);

    /** 查询用户订单列表（activityId 为空时返回该用户全部订单） */
    List<SeckillOrder> listByUserAndActivity(Long userId, Long activityId);
}
