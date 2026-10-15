package com.ghb.ecommerceflashsalesystem.activity.port;

/**
 * 秒杀库存端口（P3 Day 5：activity → seckill，库存的<b>唯一写出口</b>）。
 *
 * <p>为什么需要它：`seckill:stock:*` 的数据所有者是 seckill 域（扣减在 Lua、回补在订单消费），
 * 但"预热/重置时把配置库存刷进 Redis"过去写在 activity 的 {@code SeckillCacheService} 里 ——
 * 属于跨域写。本端口把"写动作"交还给 seckill：activity 只表达"写多少、保留多久"。</p>
 *
 * <p>实现由装配层提供（端口与适配器）：</p>
 * <ul>
 *   <li>{@code flash-sale-activity} 应用 → HTTP 适配器，调单体的
 *       {@code POST /internal/seckill/activities/{id}/stock}（带 {@code X-Internal-Token}）；</li>
 *   <li>单体（seckill 域仍在其中）→ 本地适配器，进程内调 {@code SeckillStockService}。</li>
 * </ul>
 *
 * <p>业务守卫（活动是否存在、是否进行中、时间窗）留在 activity —— 那是活动域语义；
 * seckill 侧只管"把给定值写进库存键"。</p>
 */
public interface SeckillStockPort {

    /**
     * 写入（初始化或重置）活动库存键 {@code seckill:stock:{activityId}}，并设置 TTL。
     *
     * @param activityId      活动编号
     * @param configuredStock 活动配置库存（DB {@code seckill_stock} 列）
     * @param ttlSeconds      键保留时间（秒），必须 &gt; 0
     * @return 写入后的库存值
     * @throws RuntimeException 调用失败（网络/校验）时抛出，由调用方决定降级语义
     */
    int writeStock(Long activityId, Integer configuredStock, long ttlSeconds);
}
