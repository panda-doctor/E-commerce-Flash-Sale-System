package com.ghb.ecommerceflashsalesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivitySnapshot;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.vo.ActivityMetricsVO;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivitySnapshotMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.service.metrics.MetricsService;
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
    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillActivitySnapshotMapper snapshotMapper;

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
        // 2. 订单数（成功落库的订单）
        Long orderCount = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, activityId));
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
        long queued = Math.max(0L, (streamLen == null ? 0L : streamLen) - (orderCount == null ? 0L : orderCount));
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
        snapshotMapper.insert(snapshot);
        log.info("指标快照已落库，activityId={}", activityId);
    }

}
