package com.ghb.ecommerceflashsalesystem.analytics.service.impl;

import com.ghb.ecommerceflashsalesystem.analytics.port.OrderQueryPort;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivitySnapshot;
import com.ghb.ecommerceflashsalesystem.analytics.vo.ActivityMetricsVO;
import com.ghb.ecommerceflashsalesystem.analytics.mapper.SeckillActivitySnapshotMapper;
import org.springframework.beans.factory.ObjectProvider;
import com.ghb.ecommerceflashsalesystem.analytics.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {
    private final RedisTemplate<String, Object> redisTemplate;
    /** P0 Day 4：订单数经端口由 order 域提供（避免 analytics → order 反向依赖） */
    private final OrderQueryPort orderQueryPort;
    /**
     * 快照写入器（P4 Day 10：由直连改为 {@link ObjectProvider} 延后解析）。
     *
     * <p><b>为什么</b>：快照表 {@code seckill_activity_snapshot} 的权威库是 {@code flash_sale_order}，
     * 写入口已按归属搬到 order 应用；另一个宿主（单体）虽然也扫到本类，但它**不该**、也即将
     * 不再持有 {@code analytics.mapper}。用构造器直连会让"没有该 mapper 的宿主"直接启动失败
     * （端点是入口、Bean 是装配 —— 入口搬走了装配还挂着）。改成 {@code ObjectProvider} 后：
     * <b>没有 mapper 的宿主也能装配成功</b>，只有真的调用写快照时才需要它（调用点给出可诊断的错误）。</p>
     */
    private final ObjectProvider<SeckillActivitySnapshotMapper> snapshotMapperProvider;

    /**
     * P5 Day 2：订单数的取数来源。{@code projection}（默认）= 读 Redis 投影（消费成功时累加）；
     * {@code order} = 回退到 Day 8 的跨服务计数（HTTP 调 order 的内部接口）。
     *
     * <p>为什么保留回退：投影与"DB 订单数"存在**延迟窗口**（消息尚未消费），切换默认值前
     * 需要一段双读观察期；出问题一行配置即可切回，不必回滚代码。</p>
     */
    @org.springframework.beans.factory.annotation.Value("${module.analytics.metrics-source:projection}")
    private String metricsSource = "projection";

    private long orderCount(Long activityId) {
        if ("order".equalsIgnoreCase(metricsSource)) {
            return orderQueryPort.countByActivity(activityId);
        }
        Object v = redisTemplate.opsForHash().get(
                CacheKeyConstant.SECKILL_METRIC_PREFIX + activityId,
                CacheKeyConstant.METRIC_FIELD_ORDER_COUNT);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    @Override
    public ActivityMetricsVO collectMetrics(Long activityId) {
        ActivityMetricsVO vo = new ActivityMetricsVO();
        vo.setActivityId(activityId);

        // 1. 实时库存（写入方为数值类型，统一按 Number 转 int，避免 Integer/Long 判定漏判）
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        Object stockObj = redisTemplate.opsForValue().get(stockKey);
        if (stockObj instanceof Number) {
            vo.setRedisStock(((Number) stockObj).intValue());
        } else {
            vo.setRedisStock(null); // 未预热或者已经过期
        }
        // 2. 订单数（成功落库的订单）；实现由 order 模块适配
        // P5 Day 2：默认读投影（零跨服务调用）；module.analytics.metrics-source=order 可回退
        long orderCount = orderCount(activityId);
        vo.setOrderCount(orderCount);
        vo.setSuccessCount(orderCount); // 成功数等于订单数（口径统一：以 DB 最终一致为核对源）

        // 3. 队列积压 = Stream 当前总长 - 该活动已落库订单数（下限 0）
        //    【口径复盘】Stream 消息消费 ACK 后并不会 XDEL，XLEN 恒等于历史总投递量，无法反映积压；
        //    减去该活动已落库订单数，近似得到"已入队但尚未落库的在途消息数"，可体现消费者落后程度。
        //    局限：seckill:order:stream 是全活动共享单键，多活动并存时此差值会低估其它活动的积压；
        //    本阶段演示 / 压测以单活动为主场景，该口径可接受。
        String streamKey = CacheKeyConstant.SECKILL_ORDER_STREAM;
        byte[] streamKeyBytes = redisTemplate.getStringSerializer().serialize(streamKey);
        Long streamLen = redisTemplate.execute((RedisCallback<Long>) connection -> connection.xLen(streamKeyBytes));
        long queued = Math.max(0L, (streamLen == null ? 0L : streamLen) - orderCount);
        vo.setQueuedMessageCount(queued);

        // 4. 从Hash获取三类拒绝计数
        String metricKey = CacheKeyConstant.SECKILL_METRIC_PREFIX + activityId;
        Map<Object, Object> metricMap = redisTemplate.opsForHash().entries(metricKey);

        if (metricMap != null) {
            Object rate = metricMap.get(CacheKeyConstant.METRIC_FIELD_RATE_LIMIT);
            Object dup = metricMap.get(CacheKeyConstant.METRIC_FIELD_DUPLICATE);
            Object sold = metricMap.get(CacheKeyConstant.METRIC_FIELD_SOLD_OUT);
            vo.setRateLimitRejectCount(rate != null ? ((Number) rate).longValue() : 0L);
            vo.setDuplicateRejectCount(dup != null ? ((Number) dup).longValue() : 0L);
            vo.setSoldOutRejectCount(sold != null ? ((Number) sold).longValue() : 0L);
        } else {
            vo.setRateLimitRejectCount(0L);
            vo.setDuplicateRejectCount(0L);
            vo.setSoldOutRejectCount(0L);
        }


        return vo;
    }

    @Override
    @Transactional
    public void captureSnapshot(Long activityId) {

        ActivityMetricsVO metrics = collectMetrics(activityId);
        SeckillActivitySnapshot snapshot = new SeckillActivitySnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setRedisStock(metrics.getRedisStock());
        snapshot.setOrderCount(metrics.getOrderCount());
        snapshot.setQueuedMessageCount(metrics.getQueuedMessageCount());
        snapshot.setSuccessCount(metrics.getSuccessCount());
        snapshot.setDuplicateRejectCount(metrics.getDuplicateRejectCount());
        snapshot.setRateLimitRejectCount(metrics.getRateLimitRejectCount());
        snapshot.setSoldOutRejectCount(metrics.getSoldOutRejectCount());
        snapshot.setSnapshotTime(LocalDateTime.now());
        SeckillActivitySnapshotMapper snapshotMapper = snapshotMapperProvider.getIfAvailable();
        if (snapshotMapper == null) {
            throw new IllegalStateException(
                    "当前宿主没有 seckill_activity_snapshot 的 mapper：快照写能力归 order 应用"
                            + "（它拥有权威库 flash_sale_order），activityId=" + activityId);
        }
        snapshotMapper.insert(snapshot);
        log.info("指标快照已落库，activityId={}", activityId);
    }

}
