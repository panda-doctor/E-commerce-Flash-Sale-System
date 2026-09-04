package com.ghb.ecommerceflashsalesystem.service.impl;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 秒杀服务实现：第 2 阶段 Day 2/4/5 核心链路
 * 链路：查活动（缓存优先 -> DB 回源）-> 时间窗口校验 -> status 辅助
 *       -> 整合 Lua 原子脚本（SETNX 幂等令牌 + 库存扣减 + 售罄自动回滚令牌）-> 返回 QUEUED / 抛异常
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    // ==================== v1 旧实现复盘（SeckillServerImpl 1.0 错误保留，便于对照学习） ====================
    /*
    【1. 结构错误】v1 定义为 abstract class SeckillServer，且曾在类内同时声明两个同签名 execute
       （一个有方法体、一个 abstract），Java 不允许同签名方法重复定义，编译失败。
       已修正：SeckillService（interface）+ SeckillServiceImpl（implements），见旧代码：
       // public abstract class SeckillServer {                        // v1 错误写法
       //     SeckillResponse execute(SeckillRequest request) {         // v1 与下方 abstract 同签名重复定义
       //         return null;                                          // v1 编译报错点
       //     }
       //     public abstract SeckillResponse execute(SeckillRequest request);
       // }

    【2. 链路错误】v1 直接查库、绕过预热缓存，未遵循「缓存优先 -> DB 回源」约定：
       // SeckillActivity activity = seckillActivityMapper.selectById(activityId);   // v1 错误写法
       已修正：getActivityVO() 先读 SeckillCacheService 缓存，未命中再回源数据库。

    【3. 状态判定错误】v1 强依赖 DB 的 status == RUNNING 才放行：
       // ActivityStatusEnum statusEnum = ActivityStatusEnum.fromValue(activity.getStatus());
       // if (statusEnum != ActivityStatusEnum.RUNNING) {
       //     throw new BusinessException(ResultCode.NOT_FOUND,
       //             "活动当前状态不可秒杀：" + statusEnum.getDescription());   // statusEnum 为 null 时空指针
       // }
       问题：
       a) 活动存在却被抛 40004（NOT_FOUND），语义错误，未开放应返回 40001；
       b) DB status 是创建时的快照，不会自动翻转为 RUNNING，预热后到点的活动也永远抢不到；
       c) fromValue 对非法值返回 null，调用 getDescription() 会空指针。
       已修正：以缓存时间窗口动态判定为主（startTime <= now < endTime 才放行），
       status 仅作辅助（显式 CANCELLED 提前拦截），售罄交给 Lua 兜底。

    【4. 库存键前缀错误】v1 曾误用活动 Hash 前缀拼接库存键（Lua GET Hash 键会报 WRONGTYPE）：
       // String stockKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + activityId;  // v1 错误写法
       已修正：库存预热键与扣减键均为 SECKILL_STOCK_PREFIX（seckill:stock:{activityId}）。

    【5. 时间边界】v1 用 now.isAfter(endTime)，endTime 时刻恰好相等仍放行：
       // if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) { ... }
       已修正：now >= endTime（!now.isBefore）即视为活动已结束。
    */
    private final SeckillCacheService seckillCacheService;
    private final SeckillActivityMapper seckillActivityMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> decrStockScript;
    private final RedisScript<Long> seckillExecuteScript;

    @Override
    public SeckillResponse execute(SeckillRequest request) {
        Long activityId = request.getActivityId();
        Long userId = request.getUserId();

        // 1. 查询活动：缓存优先，未命中回源数据库（修正 v1 错误点 2）
        SeckillActivityVO activityVO = getActivityVO(activityId);
        if (activityVO == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在，activityId=" + activityId);
        }

        // 2. 时间窗口动态校验：秒杀是否开放以实时时间窗为准（修正 v1 错误点 3/5）
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activityVO.getStartTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动未开始");
        }
        // v1 写 now.isAfter(endTime) 边界错误；now >= endTime 即视为已结束
        if (!now.isBefore(activityVO.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动已结束");
        }

        // 3. 状态辅助校验：仅显式取消需提前拦截；NOT_STARTED/RUNNING/ENDED 是创建时的快照
        //    （预热缓存写入的也是当时的 status），故售罄等实时状态统一交给 Lua 库存兜底
        ActivityStatusEnum statusEnum = activityVO.getStatus();
        if (statusEnum == ActivityStatusEnum.CANCELLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动已取消");
        }
/*
        // 4. SETNX 幂等令牌（Day 4）：建成功才允许继续扣库存；建失败说明该用户已抢过，直接拒绝
        // 【复盘】曾误用 SECKILL_ACTIVITY_PREFIX 拼令牌键（与 v1 库存键前缀错误同源），
        //   正确应为 SECKILL_USER_PREFIX -> seckill:user:{activityId}:{userId}

        String tokenKey = CacheKeyConstant.SECKILL_USER_PREFIX + activityId + ":" + userId;
        Boolean tokenSet = redisTemplate.opsForValue().setIfAbsent(
                tokenKey, "1",
                CacheKeyConstant.SECKILL_USER_TOKEN_TTL,
                TimeUnit.SECONDS
        );
        if (tokenSet == null || !tokenSet) {
            // 令牌已存在 ⇒ 该用户已成功秒杀过该活动（或正在处理）
            throw new BusinessException(ResultCode.DUPLICATE_PURCHASE, "请勿重复秒杀");
        }

        // 5. Lua 原子扣减库存（修正 v1 错误点 4：库存键必须是 SECKILL_STOCK_PREFIX）
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        Long result = redisTemplate.execute(decrStockScript, Arrays.asList(stockKey), 1);

        // 6. 扣减结果分支：>=0 扣减成功进入排队；<0 库存不足/键不存在
        if (result == null || result < 0) {
            // 售罄回滚（Day 4 设计取舍）：删除刚建的幂等令牌，避免「没抢到却锁 30 分钟」；
            // 「已抢到」的最终判定以订单落库为准，售罄/失败不应残留令牌（Day 5 整合 Lua 时沿用此语义）
            redisTemplate.delete(tokenKey);
            log.warn("库存不足或未预热，已回滚幂等令牌，activityId={}, userId={}", activityId, userId);
            throw new BusinessException(ResultCode.OUT_OF_STOCK, "库存不足");
        }

        // 7. 秒杀成功，返回排队中状态
        SeckillResponse seckillResponse = new SeckillResponse();
        seckillResponse.setActivityId(activityId);
        seckillResponse.setUserId(userId);
        seckillResponse.setResult("QUEUED");

        log.info("秒杀成功，activityId={}, userId={}, 剩余库存={}", activityId, userId, result);
        return seckillResponse;*/


        // 4. 执行整合 Lua 脚本（原子：建令牌 + 扣库存 + 失败回滚）
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        String tokenKey = CacheKeyConstant.SECKILL_USER_PREFIX + activityId + ":" + userId;

        Long result = redisTemplate.execute(seckillExecuteScript,
                Arrays.asList(stockKey, tokenKey),
                "1",
                CacheKeyConstant.SECKILL_USER_TOKEN_TTL);

        //处理返回值
        if (result == null) {
            // 脚本执行异常（理论上不应发生），打日志并降级为系统错误
            log.error("秒杀脚本执行返回 null，activityId={}, userId={}", activityId, userId);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
        }

        if (result >= 0) {
            //扣减成功
            SeckillResponse response = new SeckillResponse();
            response.setActivityId(activityId);
            response.setUserId(userId);
            response.setResult("QUEUED");
            log.info("秒杀成功，activityId={}, userId={}, 剩余库存={}", activityId, userId, result);
            return response;
        } else if (result == -1) {
            // 库存不足（令牌已在脚本内回滚）
            throw new BusinessException(ResultCode.OUT_OF_STOCK, "库存不足");
        } else if (result == -2) {
            // 重复秒杀（令牌已存在）
            throw new BusinessException(ResultCode.DUPLICATE_PURCHASE, "请勿重复秒杀");
        } else {
            // 未知返回值（防御）
            log.error("脚本返回未知值: {}, activityId={}, userId={}", result, activityId, userId);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "系统异常");
        }
    }

    /**
     * 查询活动视图：缓存优先，未命中回源数据库
     */
    private SeckillActivityVO getActivityVO(Long activityId) {
        // 缓存优先：已预热的活动由 SeckillCacheService 从 Redis Hash + 实时库存组装
        SeckillActivityVO cached = seckillCacheService.getActivityFromCache(activityId);
        if (cached != null) {
            log.info("命中活动缓存，activityId={}", activityId);
            return cached;
        }
        // 未命中：回源数据库兜底（与 SeckillActivityServiceImpl.getActivityDetail 行为一致）
        log.info("活动缓存未命中，回源数据库，activityId={}", activityId);
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            return null;
        }
        return convertToVO(activity);
    }

    /**
     * 实体转 VO（未预热回源场景），字段映射与 SeckillActivityServiceImpl.convertToVO 保持一致
     */
    private SeckillActivityVO convertToVO(SeckillActivity activity) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setActivityId(activity.getId());
        vo.setProductId(activity.getProductId());
        vo.setActivityName(activity.getActivityName());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setSeckillPrice(activity.getSeckillPrice());
        // 未预热回源时库存取数据库配置库存，扣减仍以 Redis 库存键实时为准
        vo.setStock(activity.getSeckillStock());
        vo.setLimitPerUser(activity.getLimitPerUser());
        if (activity.getStatus() != null) {
            vo.setStatus(ActivityStatusEnum.fromValue(activity.getStatus()));
        }
        return vo;
    }
}
