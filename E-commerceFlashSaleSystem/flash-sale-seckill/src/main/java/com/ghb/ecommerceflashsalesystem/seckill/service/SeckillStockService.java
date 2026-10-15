package com.ghb.ecommerceflashsalesystem.seckill.service;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀库存写服务（P3 Day 5：{@code seckill:stock:*} 的<b>唯一写口</b>）。
 *
 * <p>库存在本域里有三类动作：</p>
 * <ul>
 *   <li><b>扣减</b>：{@code decr_stock.lua} / {@code seckill_execute.lua}（原子扣减，已有）；</li>
 *   <li><b>回补</b>：订单死信补偿 {@code SeckillOrderConsumer#increment}（已有）；</li>
 *   <li><b>初始化 / 重置</b>：<b>本类</b> —— 过去写在 activity 的 {@code SeckillCacheService}
 *       里（跨域写），Day 5 收口到这里，对外通过内部接口暴露。</li>
 * </ul>
 *
 * <p>刻意<b>不含</b>业务守卫（活动是否存在、是否进行中、时间窗）：那些是活动域语义，
 * 由 activity 侧判断后再调用本类。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillStockService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 写入（初始化 / 重置）活动库存键，并显式设置 TTL。
     *
     * <p>{@code SET} 会清除原 TTL，因此必须重新 {@code EXPIRE}，否则会把"活动结束后自动清理"
     * 的键变成永久键（历史 E1-r 已踩过）。</p>
     *
     * @param activityId      活动编号
     * @param configuredStock 活动配置库存（DB {@code seckill_stock}）
     * @param ttlSeconds      键保留时间（秒），必须 &gt; 0
     * @return 写入后的库存值
     */
    public int writeStock(Long activityId, Integer configuredStock, long ttlSeconds) {
        if (activityId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "activityId 不能为空");
        }
        if (configuredStock == null || configuredStock < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "库存不能为负或空");
        }
        if (ttlSeconds <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "TTL 必须大于 0 秒");
        }
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        redisTemplate.opsForValue().set(stockKey, configuredStock);
        redisTemplate.expire(stockKey, ttlSeconds, TimeUnit.SECONDS);
        log.info("库存写入成功（初始化/重置），activityId={}, stock={}, ttl={}秒", activityId, configuredStock, ttlSeconds);
        return configuredStock;
    }
}
