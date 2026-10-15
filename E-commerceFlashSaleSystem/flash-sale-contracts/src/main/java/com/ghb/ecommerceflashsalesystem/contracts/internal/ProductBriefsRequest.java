package com.ghb.ecommerceflashsalesystem.contracts.internal;

import java.util.List;

/**
 * 内部接口契约：批量取商品摘要（P3 Day 5）。
 *
 * <p>调用方 activity（活动广场列表要补商品名/图/原价）、被调方 product。
 * 用批量而非逐个调用，避免"列表 50 条 → 50 次 HTTP"的 N+1。</p>
 *
 * @param productIds 商品编号集合（可为空；调用方去重）
 */
public record ProductBriefsRequest(List<Long> productIds) {
}
