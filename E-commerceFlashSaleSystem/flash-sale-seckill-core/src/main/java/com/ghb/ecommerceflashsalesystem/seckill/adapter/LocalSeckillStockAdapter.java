package com.ghb.ecommerceflashsalesystem.seckill.adapter;

import com.ghb.ecommerceflashsalesystem.activity.port.SeckillStockPort;
import com.ghb.ecommerceflashsalesystem.seckill.service.SeckillStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SeckillStockPort} 的<b>本地实现</b>（P4 Day 2 从 bootstrap 移入本库）。
 *
 * <p><b>"库存真身就在本模块"的进程都用它</b>：单体（seckill 域与 activity 域同进程）与
 * flash-sale-seckill 应用（seckill 域应用，{@link SeckillStockService} 就在自己的 classpath 上），
 * 都不需要绕一圈 HTTP。activity 应用的对应实现是 HTTP 适配器
 * （{@code remote.adapter.HttpSeckillStockAdapter}）—— 同一端口、两种装配，业务代码无感知。</p>
 *
 * <p>放在本库而不是各应用里，是因为"本地实现"只依赖 seckill 自己的服务：谁需要谁就能拿到，
 * 且不会出现两份实现同时被扫描到（HTTP 与本地装配分属不同模块，应用按需依赖）。</p>
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
