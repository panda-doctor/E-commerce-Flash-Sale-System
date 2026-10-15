package com.ghb.ecommerceflashsalesystem.ai.adapter;

import com.ghb.ecommerceflashsalesystem.activity.port.SeckillStockPort;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ai 侧的 {@link SeckillStockPort} 实现：<b>明确拒绝</b>（P3 Day 5）。
 *
 * <p>ai 服务不做预热、也不做库存重置（那是 activity 管理端编排、seckill 域落库的动作）。
 * 本类之所以存在，唯一原因是 activity-core 的 {@code SeckillCacheService} 会被组件扫描到、
 * 它需要一个该端口的 Bean。</p>
 *
 * <p>刻意 <b>fail-fast</b> 而不是"静默成功"：一旦真有代码路径走到这里，说明依赖方向用错了
 * —— 应该立刻响亮地失败，而不是悄悄写坏库存或假装写成功。</p>
 */
@Slf4j
@Component
public class UnsupportedSeckillStockAdapter implements SeckillStockPort {

    @Override
    public int writeStock(Long activityId, Integer configuredStock, long ttlSeconds) {
        throw new BusinessException(ResultCode.SYSTEM_ERROR,
                "ai 服务不支持写入秒杀库存（库存写入属 activity 管理端编排 + seckill 域执行）");
    }
}
