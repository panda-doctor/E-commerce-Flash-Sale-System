package com.ghb.ecommerceflashsalesystem.order.service;

import com.ghb.ecommerceflashsalesystem.analytics.service.SeckillRankService;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.order.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * P5 Day 3：**在 Java 侧**重建 analytics 投影（榜单 ZSet / 订单号映射 / 订单数计数）。
 *
 * <p><b>为什么必须走 Java</b>：投影由 {@code SeckillRankServiceImpl} 通过应用共享的
 * {@code RedisTemplate} 写入，值经 Jackson 序列化。早期尝试用 PowerShell 直接发 RESP 裸字符串，
 * 读回来时反序列化失败（{@code SerializationException} → 指标/榜单 500）。因此回填必须复用
 * **同一个写入器**：这里逐条调用 {@link SeckillRankService#recordSuccess}，序列化天然一致。</p>
 *
 * <p><b>幂等</b>：{@code recordSuccess} 内部已用 {@code SETNX seckill:dedupe:order:<orderNo>}
 * 与 ZSet 的 NX 写入去重 —— 重复回填不会重复计数，因此本服务可以安全地反复执行。</p>
 *
 * <p><b>时间序</b>：{@code successTime} 传 {@code null} → 实现回退当前时间，条目按"回填顺序"排；
 * 对已有数据的活动重建时以 {@code created_at} 升序回放（mapper 已排序），相对顺序即真实下单顺序。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionRebuildService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillRankService seckillRankService;

    /**
     * 重建单个活动的投影。
     *
     * @return 回放的订单条数
     */
    public int rebuild(Long activityId) {
        List<SeckillOrder> orders = seckillOrderMapper.selectSuccessByActivityId(activityId);
        for (SeckillOrder order : orders) {
            seckillRankService.recordSuccess(activityId, order.getUserId(), null, order.getOrderNo());
        }
        log.info("P5 投影回填完成：activityId={} 订单={} 条", activityId, orders.size());
        return orders.size();
    }
}
