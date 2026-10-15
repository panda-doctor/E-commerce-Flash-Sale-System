package com.ghb.ecommerceflashsalesystem.contracts.internal;

/**
 * 内部接口契约：设置活动秒杀库存（P3 Day 5）。
 *
 * <p>调用方 activity、被调方 seckill。刻意只用 JDK 类型（不引用业务实体/枚举），
 * 使契约模块保持"零业务依赖"（架构规则 R2）。</p>
 *
 * @param stock      要写入的库存值（来自活动配置 {@code seckill_activity.seckill_stock}）
 * @param ttlSeconds 键保留时间（秒）：SET 会清除原 TTL，必须显式设置，避免留下永久键
 */
public record SeckillStockRequest(Integer stock, Long ttlSeconds) {
}
