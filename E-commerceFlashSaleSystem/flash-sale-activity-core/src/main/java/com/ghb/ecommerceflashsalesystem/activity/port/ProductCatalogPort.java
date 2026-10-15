package com.ghb.ecommerceflashsalesystem.activity.port;

import java.util.Collection;
import java.util.Map;

/**
 * 商品目录端口（P3 Day 5：activity → product，activity 唯一的跨域读出口）。
 *
 * <p>为什么需要它：activity 应用过去用 {@code ProductMapper} 直读 product 表（创建活动校验商品存在、
 * 列表页补商品名/图/原价）。这带来两个问题：① 违反"谁的数据谁提供"；② 一旦 activity 的 datasource
 * 切到独立 schema（P4 首日"读切换"），新库里没有 product 表，校验会直接失败。</p>
 *
 * <p>实现由装配层提供：activity 应用 → HTTP 适配器（调 product 服务的公开接口，{@code lb://flash-sale-product}）；
 * 单体 → 本地适配器（进程内走 product-core 的 mapper）。</p>
 */
public interface ProductCatalogPort {

    /**
     * 单个商品是否存在（创建/更新活动时校验关联商品）。
     *
     * <p><b>约定</b>：调用失败（服务不可用/超时）必须抛异常，**不得**返回 {@code false} ——
     * 否则会把"下游挂了"误判成"商品不存在"，让调用方拿到错误的业务结论。</p>
     */
    boolean exists(Long productId);

    /**
     * 批量取商品摘要（活动广场列表补商品名/图/原价，避免 N+1）。
     *
     * @param productIds 商品编号集合（可为空）
     * @return productId → 摘要；查不到的商品不会出现在返回 map 里
     */
    Map<Long, ProductBrief> findBriefs(Collection<Long> productIds);

    /**
     * 商品摘要（列表展示所需的最小字段集）。
     *
     * @param productId     商品编号
     * @param name          商品名
     * @param imageUrl      主图 URL
     * @param originalPrice 原价（分）
     */
    record ProductBrief(Long productId, String name, String imageUrl, Long originalPrice) {
    }
}
