package com.ghb.ecommerceflashsalesystem.adapter;

import com.ghb.ecommerceflashsalesystem.activity.port.SeckillStockPort;
import com.ghb.ecommerceflashsalesystem.seckill.service.SeckillStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SeckillStockPort} 的<b>本地实现</b>（单体专用）。
 *
 * <p>单体里 seckill 域就在同一个进程内（flash-sale-seckill 模块），因此不需要绕一圈 HTTP：
 * 直接调用 {@link SeckillStockService}。activity 应用的对应实现是 HTTP 适配器
 * （{@code HttpSeckillStockAdapter}）—— 同一端口、两种装配，业务代码无感知。</p>
 */
@Component
@RequiredArgsConstructor
public class LocalSeckillStockAdapter implements SeckillStockPort {

    private final SeckillStockService seckillStockService;

    @Override
    public int writeStock(Long activityId, Integer configuredStock, long ttlSeconds) {
        return seckillStockService.writeStock(activityId, configuredStock, ttlSeconds);
    }
}
