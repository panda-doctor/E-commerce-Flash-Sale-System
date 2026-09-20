package com.ghb.ecommerceflashsalesystem.service.cache;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.enums.PreheatStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀缓存服务：活动预热 / 库存重置（分布式锁守护写入口）+ 活动缓存读取
 *
 * 【Day 5 重构复盘】曾出现：无关 import（Redisson/CacheKey/kafka SslBundleSslEngineFactory）、
 * doPreheat 重复定义、resetStock 被误写到类外、`long ttl = ...; } + CONSTANT;` 残缺语句等编译错误。
 * 本版重写为结构清晰实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SeckillActivityMapper seckillActivityMapper;
    private final RedissonClient redissonClient;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 锁参数常量
    private static final long LOCK_WAIT_TIME = 5;   // 最多等待 5 秒
    private static final long LOCK_LEASE_TIME = 30; // 锁持有 30 秒自动释放（防死锁）

    /**
     * 预热活动（带分布式锁，保证并发安全）
     *
     * 为什么预热需要锁而 execute 不需要？
     * - 预热是"查状态 → 写缓存 → 更新 DB 状态"的【多步读改写】，非原子，并发会重复预热/覆盖写，需锁串行化；
     * - execute 的防重 + 扣减已由 Lua 脚本原子完成，加锁反而降并发，故主链不加锁。
     * 锁内做双重检查：拿到锁后重新查库，等锁期间若已被别人预热则直接拒绝。
     */
    public void preheatActivity(Long activityId) {
        String lockKey = CacheKeyConstant.SECKILL_LOCK_PREFIX + "preheat:" + activityId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取预热锁超时，请稍后重试");
            }

            // 1. 锁内查活动（双重检查：等锁期间可能已被别的线程预热）
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if (activity == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
            }

            // 2. 防重预热（锁内检查 DB 最新状态）
            if (activity.getPreheatStatus() != null
                    && PreheatStatusEnum.PREHEATED.getCode() == activity.getPreheatStatus()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "活动已预热，请勿重复预热");
            }

            // 3. 时间窗口校验
            LocalDateTime now = LocalDateTime.now();
            if (activity.getEndTime().isBefore(now)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "活动已结束，无法预热");
            }

            // 4. 执行预热（写缓存 + 更新 DB）
            doPreheat(activity, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "预热被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("预热锁已释放，activityId={}", activityId);
            }
        }
    }

    /**
     * 实际预热逻辑（由预热锁保护执行）
     */
    private void doPreheat(SeckillActivity activity, LocalDateTime now) {
        Long activityId = activity.getId();
        try {
            // 写活动 Hash
            String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + activityId;
            Map<String, Object> hashFields = new HashMap<>();
            hashFields.put("activityId", activity.getId());
            hashFields.put("productId", activity.getProductId());
            hashFields.put("activityName", activity.getActivityName());
            hashFields.put("startTime", activity.getStartTime().format(DATE_TIME_FORMATTER));
            hashFields.put("endTime", activity.getEndTime().format(DATE_TIME_FORMATTER));
            hashFields.put("seckillPrice", activity.getSeckillPrice());
            hashFields.put("seckillStock", activity.getSeckillStock());
            hashFields.put("limitPerUser", activity.getLimitPerUser());
            hashFields.put("status", activity.getStatus());
            redisTemplate.opsForHash().putAll(activityKey, hashFields);

            // 写库存 String
            String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
            redisTemplate.opsForValue().set(stockKey, activity.getSeckillStock());

            // 设置 TTL（活动结束时间 + 缓冲）
            long ttlSeconds = Duration.between(now, activity.getEndTime()).getSeconds()
                    + CacheKeyConstant.SECKILL_CACHE_TTL_EXTRA;
            if (ttlSeconds > 0) {
                redisTemplate.expire(activityKey, ttlSeconds, TimeUnit.SECONDS);
                redisTemplate.expire(stockKey, ttlSeconds, TimeUnit.SECONDS);
            } else {
                // 极端情况：活动已结束但未校验到（防御），设置最小 TTL
                redisTemplate.expire(activityKey, 60, TimeUnit.SECONDS);
                redisTemplate.expire(stockKey, 60, TimeUnit.SECONDS);
            }

            // 更新数据库预热状态为"已预热"
            UpdateWrapper<SeckillActivity> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", activityId).set("preheat_status", PreheatStatusEnum.PREHEATED.getCode());
            seckillActivityMapper.update(null, updateWrapper);
            log.info("活动预热成功，activityId={}, ttl={}秒", activityId, ttlSeconds);
        } catch (Exception e) {
            // 预热失败，更新数据库状态为"预热失败"
            UpdateWrapper<SeckillActivity> failWrapper = new UpdateWrapper<>();
            failWrapper.eq("id", activityId).set("preheat_status", PreheatStatusEnum.PREHEAT_FAILED.getCode());
            seckillActivityMapper.update(null, failWrapper);
            log.error("活动预热失败，activityId={}", activityId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "活动预热失败，请稍后重试");
        }
    }

    /**
     * 重置库存：把数据库配置库存刷回 Redis（预热前 / 活动结束后的运维、压测复位用），加锁防并发写错乱。
     * E1-r：活动进行中禁止重置（守卫见方法内），防止把 Redis 已扣减的库存用 DB 配置库存"复活"。
     */
    public void resetStock(Long activityId) {
        String lockKey = CacheKeyConstant.SECKILL_LOCK_PREFIX + "reset:" + activityId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取重置锁超时，请稍后重试");
            }
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if (activity == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
            }
            // E1-r：活动进行中禁止重置库存（防止把 Redis 已扣减库存用 DB 配置库存"复活"）
            LocalDateTime now = LocalDateTime.now();
            if (activity.getStartTime() != null && now.isAfter(activity.getStartTime())
                    && (activity.getEndTime() == null || now.isBefore(activity.getEndTime()))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "活动进行中，禁止重置库存");
            }
            String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
            redisTemplate.opsForValue().set(stockKey, activity.getSeckillStock());

            // 刷新 TTL（活动结束时间 + 缓冲）
            long ttlSeconds = Duration.between(LocalDateTime.now(), activity.getEndTime()).getSeconds()
                    + CacheKeyConstant.SECKILL_CACHE_TTL_EXTRA;
            if (ttlSeconds > 0) {
                redisTemplate.expire(stockKey, ttlSeconds, TimeUnit.SECONDS);
            }
            log.info("库存重置成功，activityId={}, stock={}", activityId, activity.getSeckillStock());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "重置库存被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("重置锁已释放，activityId={}", activityId);
            }
        }
    }

    /**
     * 活动信息变更后的缓存失效入口（R4）：
     * 活动"更新"（改价/改库存/改时间窗等）成功后必须调用，删除活动 Hash 与库存键，
     * 否则详情与 execute 会继续命中旧缓存（旧价旧库存落单）。
     *
     * @param activityId 活动ID
     * @return 是否删除了至少一个缓存键
     */
    public boolean evictActivityCache(Long activityId) {
        String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + activityId;
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        boolean deleted = Boolean.TRUE.equals(redisTemplate.delete(activityKey))
                | Boolean.TRUE.equals(redisTemplate.delete(stockKey));
        if (deleted) {
            log.warn("活动变更，已失效预热缓存，activityId={}", activityId);
        }
        return deleted;
    }

    /**
     * 将活动的预热状态回置为"未预热"（R4：活动信息更新后需重新预热才能生效）
     */
    public void resetPreheatStatusToUnpreheated(Long activityId) {
        UpdateWrapper<SeckillActivity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", activityId)
                .set("preheat_status", PreheatStatusEnum.NOT_PREHEATED.getCode());
        seckillActivityMapper.update(null, wrapper);
        log.info("活动预热状态已回置为未预热，activityId={}", activityId);
    }

    public SeckillActivityVO getActivityFromCache(Long activityId) {
        String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + activityId;
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;

        // 1. 获取活动Hash 数据
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(activityKey);
        if (entries == null || entries.isEmpty()) {
            log.warn("活动缓存不存在，activityId={}", activityId);
            return null;
        }

        // ===== 以下为 panda 原错误实现，保留注释便于复盘（编译不过 + 逻辑错误）=====
        // [错误1] 库存键是独立的 String 键，不是 Hash 的 field，须用 opsForValue().get(stockKey) 读。
        // [错误2] VO.seckillPrice 是 Long，不能 setSeckillPrice(new BigDecimal(...))。
        // [错误3] SeckillActivityVO 没有 seckillStock 字段，库存统一存 vo.stock（Integer）。
        // [错误4] VO.startTime/endTime 是 LocalDateTime，须用 DateTimeFormatter 解析字符串。
        // [错误5] VO.status 是 ActivityStatusEnum，须 fromValue(Integer) 反查。

        // 2. 获取实时库存（独立 String 键）
        Integer realStock = getStockFromCache(activityId);
        if (realStock == null) {
            log.warn("库存缓存不存在，activityId={}", activityId);
            return null;
        }

        // 3. 组装 VO
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setActivityId(Long.valueOf(entries.get("activityId").toString()));
        vo.setProductId(Long.valueOf(entries.get("productId").toString()));
        vo.setActivityName(entries.get("activityName").toString());
        vo.setStartTime(LocalDateTime.parse(entries.get("startTime").toString(), DATE_TIME_FORMATTER));
        vo.setEndTime(LocalDateTime.parse(entries.get("endTime").toString(), DATE_TIME_FORMATTER));
        vo.setSeckillPrice(Long.valueOf(entries.get("seckillPrice").toString()));
        vo.setLimitPerUser(Integer.valueOf(entries.get("limitPerUser").toString()));
        vo.setStatus(ActivityStatusEnum.fromValue(Integer.parseInt(entries.get("status").toString())));
        vo.setStock(realStock);
        // 配置总库存：预热时已写入 Hash 的 seckillStock 字段（供详情"已抢 %"进度计算）
        Object seckillStockObj = entries.get("seckillStock");
        if (seckillStockObj != null) {
            vo.setTotalStock(Integer.valueOf(seckillStockObj.toString()));
        }
        return vo;
    }

    public Integer getStockFromCache(Long activityId) {
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        Object stockObj = redisTemplate.opsForValue().get(stockKey);
        if (stockObj == null) {
            return null;
        }
        return Integer.valueOf(stockObj.toString());
    }


}
