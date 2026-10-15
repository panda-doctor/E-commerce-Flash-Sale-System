package com.ghb.ecommerceflashsalesystem.contracts.internal;

/**
 * 内部接口契约：商品摘要（P3 Day 5）。
 *
 * <p>只带"列表展示"所需的最小字段集 —— 跨服务契约刻意比实体窄，避免把商品表结构
 * 泄漏给调用方（调用方不应依赖本不存在的字段）。</p>
 *
 * @param productId     商品编号
 * @param name          商品名
 * @param imageUrl      主图 URL
 * @param originalPrice 原价（分）
 */
public record ProductBriefView(Long productId, String name, String imageUrl, Long originalPrice) {
}
