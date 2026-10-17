package com.ghb.ecommerceflashsalesystem.analytics.service.impl;


import com.ghb.ecommerceflashsalesystem.analytics.port.OrderQueryPort;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.analytics.vo.RankEntryVO;
import com.ghb.ecommerceflashsalesystem.analytics.service.SeckillRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillRankServiceImpl implements SeckillRankService {

    private final RedisTemplate<String, Object> redisTemplate;
    // P5 Day 2：读侧改读 Redis 投影（榜单 ZSet + 订单号映射 Hash + 订单数计数），
    // 因此不再需要 OrderQueryPort —— analytics 与 order 之间从此没有同步调用。

    @Override
    public void recordSuccess(Long activityId, Long userId, Long successTime, String orderNo) {
        // 榜单非关键路径，异常只记录日志，不影响主流程
        try {
            String key = CacheKeyConstant.SECKILL_RANK_PREFIX + activityId;
            // score = 抢单成功时刻（execute 侧 requestTime，越早越小）。
            // 用消费端当前时间会失真：同一消费批次的多条消息会在同一毫秒入榜，先后序被打乱。
            // 兼容早期无 requestTime 的消息回退当前时间。
            long score = successTime != null ? successTime : System.currentTimeMillis();
            // Spring Data Redis 的 addIfAbsent 对应 ZADD NX：
            // member 已存在时不修改不重复计数 → 天然幂等（PEL 重放 / 幂等路径重复调用都不脏榜）
            Boolean added = redisTemplate.opsForZSet().addIfAbsent(key, userId, score);
            if (Boolean.TRUE.equals(added)) {
                log.debug("用户入榜成功，activityId={}, userId={}", activityId, userId);
            } else {
                log.debug("用户已在榜中，activityId={}, userId={}（幂等）", activityId, userId);
            }
            // 榜单键暂不设过期（复盘需要），由业务侧按活动维度清理
        } catch (Exception e) {
            log.warn("记录榜单失败，activityId={}, userId={}", activityId, userId, e);
        }

        // P5：投影写入**独立**处理 —— 不能与榜单共用 catch-all。
        // 2026-10-17 实测教训：共用 catch 时 HINCRBY 的失败被静默吞掉（1000 单只累计到 320），
        // 调用方与指标都看不出问题。失败必须计数 + WARN，让漂移可被发现。
        writeProjection(activityId, userId, orderNo);
    }

    /**
     * 写入订单号映射与订单数计数（幂等：映射覆盖写，计数以 orderNo 做 SETNX 去重）。
     */
    private void writeProjection(Long activityId, Long userId, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        try {
            // ① 订单号映射：榜单展示直接读它，读侧不再回调 order 服务
            redisTemplate.opsForHash().put(
                    CacheKeyConstant.SECKILL_RANK_ORDER_PREFIX + activityId,
                    String.valueOf(userId), orderNo);
            // ② 订单数计数：Stream 是至少一次投递 → 以 orderNo 为幂等键（7 天覆盖重投递窗口）
            Boolean first = redisTemplate.opsForValue().setIfAbsent(
                    CacheKeyConstant.SECKILL_DEDUPE_ORDER_PREFIX + orderNo, "1",
                    java.time.Duration.ofDays(7));
            if (Boolean.TRUE.equals(first)) {
                redisTemplate.opsForHash().increment(
                        CacheKeyConstant.SECKILL_METRIC_PREFIX + activityId,
                        CacheKeyConstant.METRIC_FIELD_ORDER_COUNT, 1);
            }
        } catch (Exception e) {
            log.warn("投影写入失败（可由对账发现），activityId={}, userId={}, orderNo={}",
                    activityId, userId, orderNo, e);
            countProjectionFailure(activityId);
        }
    }

    /** 投影失败计数（自身失败也不能抛：只打日志，避免掩盖原始异常）。 */
    private void countProjectionFailure(Long activityId) {
        try {
            redisTemplate.opsForHash().increment(
                    CacheKeyConstant.SECKILL_METRIC_PREFIX + activityId,
                    CacheKeyConstant.METRIC_FIELD_PROJECTION_FAILURE, 1);
            // P6 Day 2：全局累计（供健康检查 O(1) 采集，避免健康检查去扫活动维度）
            redisTemplate.opsForValue().increment(CacheKeyConstant.SECKILL_PROJECTION_FAILURE_TOTAL);
        } catch (Exception e) {
            log.error("投影失败计数也无法写入（Redis 可能不可用），activityId={}", activityId, e);
        }
    }
    /**
     * P5 Day 2：从投影 Hash 批量读订单号（`HMGET`）。缺失的条目返回空串（与旧实现口径一致：
     * 早期未入投影的历史榜单成员会暂时显示空订单号，回填脚本可补齐）。
     */
    private Map<Long, String> orderNosOf(Long activityId, List<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Object> fields = userIds.stream().map(id -> (Object) String.valueOf(id)).collect(Collectors.toList());
        List<Object> values = redisTemplate.opsForHash()
                .multiGet(CacheKeyConstant.SECKILL_RANK_ORDER_PREFIX + activityId, fields);
        for (int i = 0; i < userIds.size(); i++) {
            Object v = (values == null || i >= values.size()) ? null : values.get(i);
            if (v != null) {
                result.put(userIds.get(i), String.valueOf(v));
            }
        }
        return result;
    }

    @Override
    public List<RankEntryVO> topN (Long activityId, int top) {
        // 限制top 范围
        top = Math.min(Math.max(top, 1), CacheKeyConstant.RANK_MAX_TOP);
        String key = CacheKeyConstant.SECKILL_RANK_PREFIX + activityId;
        // ZRANGE key 0 top-1 WITHSCORES (升序，早者在前)
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(key, 0, top - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        //提取userId 集合
        List<Long> userIds = tuples.stream()
                .map(t -> ((Number) t.getValue()).longValue())
                .collect(Collectors.toList());

        // 批量查询订单号映射（同一活动、同一用户唯一订单）；实现由 order 模块适配
        Map<Long, String> userIdToOrder = orderNosOf(activityId, userIds);   // P5：读投影 Hash（无跨服务调用）
        // 构造返回列表
        List<RankEntryVO> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            long userId = ((Number) tuple.getValue()).longValue();
            Double score = tuple.getScore();
            String orderNo = userIdToOrder.getOrDefault(userId, "");
            result.add(RankEntryVO.builder()
                    .rank(rank++)
                    .userId(userId)
                    .score(score != null ? score.longValue() : 0L)
                    .orderNo(orderNo)
                    .build());
        }
        return result;
    }
}
