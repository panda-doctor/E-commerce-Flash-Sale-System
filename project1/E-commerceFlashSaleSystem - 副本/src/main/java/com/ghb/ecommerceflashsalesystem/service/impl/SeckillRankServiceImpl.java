package com.ghb.ecommerceflashsalesystem.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.vo.RankEntryVO;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.service.rank.SeckillRankService;
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
    private final SeckillOrderMapper seckillOrderMapper;

    @Override
    public void recordSuccess(Long activityId, Long userId, Long successTime) {
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

        // 批量查询订单，获取 orderNo（同一活动、同一用户唯一订单）
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getActivityId, activityId)
                .in(SeckillOrder::getUserId, userIds)
                .orderByAsc(SeckillOrder::getCreatedAt);// 按落库顺序排列，保证与榜单一致

        List<SeckillOrder> orders = seckillOrderMapper.selectList(wrapper);
        // 组装 userId -> orderNo 映射
        Map<Long, String> userIdToOrder = orders.stream()
                .collect(Collectors.toMap(
                        SeckillOrder::getUserId,
                        SeckillOrder::getOrderNo
                ));
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
