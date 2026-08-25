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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillCacheService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final SeckillActivityMapper seckillActivityMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void preheatActivity(Long activityId) {
        // 1. 查询活动
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
        }

        // 2.防重预热
        if (activity.getPreheatStatus() != null && PreheatStatusEnum.PREHEATED.getCode() == activity.getPreheatStatus()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动预热成功！！！不要重复，傻鸟！！！");
        }

        // 3.时间窗口校验
        LocalDateTime now = LocalDateTime.now();
        if (activity.getEndTime().isBefore(now)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活动已结束，无法预热");
        }

        try {
            // 4.写活动 Hash
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
            hashFields.put("status", activity.getStatus()); // Integer code

            redisTemplate.opsForHash().putAll(activityKey, hashFields);

            // 5. 写库存 String
            String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
            redisTemplate.opsForValue().set(stockKey, activity.getSeckillStock());

            // 6. 设置 TTL（活动结束时间 + 缓冲）
            long ttlSeconds = Duration.between(now, activity.getEndTime()).getSeconds() + CacheKeyConstant.SECKILL_CACHE_TTL_EXTRA;
            if (ttlSeconds > 0) {
                redisTemplate.expire(activityKey, ttlSeconds, TimeUnit.SECONDS);
                redisTemplate.expire(stockKey, ttlSeconds, TimeUnit.SECONDS);
            } else {
                // 极端情况：活动已结束但未校验到（防御），设置最小TTL
                redisTemplate.expire(activityKey, 60, TimeUnit.SECONDS);
                redisTemplate.expire(stockKey, 60, TimeUnit.SECONDS);
            }
            // 7. 更新数据库预热状态为"已预热"
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
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "活动预热失败：" + e.getMessage());
        }
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
        // [错误1] 112行：库存键是独立的 String 键，不是 Hash 的 field。
        //   原句 Object stockObj = redisTemplate.opsForHash().get(stockKey);
        //   原因：preheatActivity 中库存用 opsForValue().set(stockKey, ...) 存的是独立的 String 键，
        //         它的类型是 String（RedisTemplate<String,Object> 的 K），不是 Hash 的 H。调 opsForHash().get
        //         需要 (H, HK) 两个参数，只传一个 String 必然编译错（"方法 get 参数数量不同"）。
        //   正确：库存必须用 opsForValue().get(stockKey) 读（本方法下方 getStockFromCache 就是正确写法）。
        //Object stockObj = redisTemplate.opsForHash().get(stockKey);
        //
        // [错误2] 124行：setSeckillPrice(new BigDecimal(...)) —— SeckillActivityVO.seckillPrice 是 Long，
        //   BigDecimal 不能自动转 Long，编译报"无法转换"。
        // [错误3] 125行：setSeckillStock(entries.get("seckillStock")) —— 编译报"找不到方法 setSeckillStock"，
        //   SeckillActivityVO 没有 seckillStock 字段！库存统一存在 vo.stock（一个 Integer 字段）。
        //   正确：只设 vo.setStock(realStock)，不要再设 seckillStock。
        // [错误4] 122/123行：setStartTime(String)/setEndTime(String) —— VO 的 startTime/endTime 是 LocalDateTime，
        //   String 无法转 LocalDateTime。必须用 DateTimeFormatter 把 "yyyy-MM-dd HH:mm:ss" 字符串解析回 LocalDateTime。
        // [错误5] 127行：setStatus(Integer) —— VO 的 status 是 ActivityStatusEnum 枚举，不是 Integer。
        //   必须用 ActivityStatusEnum.fromValue(integerCode) 反查枚举。

        //2.获取实时库存（正确写法：库存是独立 String 键，用 opsForValue 读）
        Integer realStock = getStockFromCache(activityId);   // 复用下方方法，避免重复实现
        if (realStock == null) {
            // 库存缓存不存在，可能未预热或已过期
            log.warn("库存缓存不存在，activityId={}", activityId);
            return null;
        }

        // 3. 组装 VO
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setActivityId(Long.valueOf(entries.get("activityId").toString()));
        vo.setProductId(Long.valueOf(entries.get("productId").toString()));
        vo.setActivityName(entries.get("activityName").toString());
        // 时间字符串 -> LocalDateTime：用与 preheatActivity 相同格式的格式化器解析
        vo.setStartTime(LocalDateTime.parse(entries.get("startTime").toString(), DATE_TIME_FORMATTER));
        vo.setEndTime(LocalDateTime.parse(entries.get("endTime").toString(), DATE_TIME_FORMATTER));
        vo.setSeckillPrice(Long.valueOf(entries.get("seckillPrice").toString()));
        vo.setLimitPerUser(Integer.valueOf(entries.get("limitPerUser").toString()));
        // 状态：Integer 码 -> 枚举（用 ActivityStatusEnum.fromValue 反查，语义化）
        vo.setStatus(ActivityStatusEnum.fromValue(Integer.parseInt(entries.get("status").toString())));
        vo.setStock(realStock);   // 设置实时库存

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
