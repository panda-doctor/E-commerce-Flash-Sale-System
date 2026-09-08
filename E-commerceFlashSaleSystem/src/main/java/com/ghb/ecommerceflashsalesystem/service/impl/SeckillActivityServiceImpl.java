package com.ghb.ecommerceflashsalesystem.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.common.util.IdGenerator;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.ActivityRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.ActivityCheckResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.ActivityItemVO;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 秒杀活动服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillActivityServiceImpl implements SeckillActivityService {
    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillCacheService seckillCacheService;
    private final ProductMapper productMapper;

    // ========== 方法⓪：活动广场列表（附带商品信息与实时库存） ==========

    @Override
    public List<ActivityItemVO> listActivities() {
        List<SeckillActivity> activities = seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .orderByDesc(SeckillActivity::getStartTime)
                        .last("LIMIT 50"));
        if (activities == null || activities.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量取商品信息（productIds 去重一次 IN 查询，避免列表 N+1）
        List<Long> productIds = activities.stream()
                .map(SeckillActivity::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

        return activities.stream().map(activity -> {
            ActivityItemVO vo = new ActivityItemVO();
            vo.setActivityId(activity.getId());
            vo.setProductId(activity.getProductId());
            vo.setActivityName(activity.getActivityName());
            vo.setStartTime(activity.getStartTime());
            vo.setEndTime(activity.getEndTime());
            vo.setSeckillPrice(activity.getSeckillPrice());
            vo.setStatus(ActivityStatusEnum.fromValue(activity.getStatus()));

            Product product = productMap.get(activity.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductImage(product.getImageUrl());
                vo.setOriginalPrice(product.getOriginalPrice());
            }
            // 实时库存：预热后取 Redis；未预热回退 DB 配置库存
            Integer cacheStock = seckillCacheService.getStockFromCache(activity.getId());
            vo.setStock(cacheStock != null ? cacheStock : activity.getSeckillStock());
            vo.setTotalStock(activity.getSeckillStock());
            return vo;
        }).collect(Collectors.toList());
    }

    // ========== 方法①：创建/更新活动 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createActivity(ActivityRequest request) {
        validateActivityRequest(request);
        Long activityId = request.getActivityId();
         //判断是创建还是更新
        if (activityId == null) {
            // ====创建=====
            SeckillActivity activity = new SeckillActivity();
            activity.setId(IdGenerator.nextId());
            activity.setProductId(request.getProductId());
            activity.setActivityName(request.getActivityName());
            activity.setStartTime(request.getStartTime());
            activity.setEndTime(request.getEndTime());
            activity.setSeckillPrice(request.getSeckillPrice());
            activity.setSeckillStock(request.getSeckillStock());
            activity.setLimitPerUser(request.getLimitPerUser());

            // 新建活动默认状态：未开始，未预热
            activity.setStatus(ActivityStatusEnum.NOT_STARTED.getCode());
            activity.setPreheatStatus(0);   // 0=未预热
            activity.setVersion(0);

            seckillActivityMapper.insert(activity); //插入活动
            log.info("创建活动成功，activityId={}", activity.getId());
            return activity.getId();
        }else{
            // ===更新===
            //先判断活动是否存在
            SeckillActivity activityIdExisting = seckillActivityMapper.selectById(activityId);
            if (activityIdExisting == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在，activityId=" + activityId);
            }
            // 只更新允许业务修改的字段（不修改 status、preheat_status、version 等系统字段）
            activityIdExisting.setProductId(request.getProductId());
            activityIdExisting.setActivityName(request.getActivityName());
            activityIdExisting.setStartTime(request.getStartTime());
            activityIdExisting.setEndTime(request.getEndTime());
            activityIdExisting.setSeckillPrice(request.getSeckillPrice());
            activityIdExisting.setSeckillStock(request.getSeckillStock());
            activityIdExisting.setLimitPerUser(request.getLimitPerUser());

            int updatedRows = seckillActivityMapper.updateById(activityIdExisting);
            // 乐观锁（@Version）并发保护：版本不一致时更新 0 行，需提示刷新重试
            if (updatedRows == 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "活动已被他人修改，请刷新后重试，activityId=" + activityId);
            }
            // R4 修复：更新成功后必须失效预热缓存并回置预热状态为"未预热"，
            // 否则详情与 execute 继续命中旧缓存（旧价旧库存落单）；
            // 新版生效需管理员在管理台重新点击"预热"（预热方法校验通过后会重建缓存）。
            try {
                seckillCacheService.evictActivityCache(activityId);
            } catch (Exception e) {
                // 缓存失效失败不应回滚 DB 更新；记日志并交由后续预热幂等兜底
                log.error("更新活动后失效缓存异常，activityId={}", activityId, e);
            }
            // 回置预热状态：已预热 → 未预热（DB 为唯一事实源，预热锁内双重检查据此生效）
            seckillCacheService.resetPreheatStatusToUnpreheated(activityId);
            log.info("更新活动成功，已失效预热缓存并回置预热状态，activityId={}", activityId);
            return activityId;
        }
    }
    // ========== 方法②：查询活动详情（缓存优先） ==========

    @Override
    public SeckillActivityVO getActivityDetail(Long activityId) {
        //尝试从缓存中读取（已预热）
        SeckillActivityVO cachedVO = seckillCacheService.getActivityFromCache(activityId);
        if(cachedVO != null){
            log.info("命中活动缓存，activityId={}", activityId);
            // 注意：缓存中的 stock 已经是实时库存，从 seckill:stock:{id} 读取，getActivityFromCache 已填充
            return cachedVO;
        }
        // 2. 缓存未命中，查询数据库
        log.info("活动缓存未命中，查询数据库，activityId={}", activityId);
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if(activity == null){
            return null;
        }
        // 3. 转换为 VO（使用数据库中的库存字段）
        return convertToVO(activity);
    }
    /**
     * 实体转 VO（用于未预热场景）
     */
    private SeckillActivityVO convertToVO(SeckillActivity activity) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setActivityId(activity.getId());
        vo.setProductId(activity.getProductId());
        vo.setActivityName(activity.getActivityName());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setSeckillPrice(activity.getSeckillPrice());
        // 未预热时，库存取数据库的 seckill_stock
        vo.setStock(activity.getSeckillStock());
        // 配置总库存：详情页"已抢 %"进度数据源（预热缓存路径在 getActivityFromCache 补）
        vo.setTotalStock(activity.getSeckillStock());
        vo.setLimitPerUser(activity.getLimitPerUser());

        // 状态转换
        Integer statusCode = activity.getStatus();
        if(statusCode != null){
            vo.setStatus(ActivityStatusEnum.fromValue(statusCode));
        }
        return vo;
    }
    // ========== 方法③：校验用户可否参与 ==========
    @Override
    public ActivityCheckResponse checkActivity(Long activityId, Long userId) {

        // 先尝试从缓存获取活动信息（更实时）
        SeckillActivityVO activityVO = seckillCacheService.getActivityFromCache(activityId);
        if(activityVO == null){
            //缓存未命中，查数据库
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if(activity == null){
                throw new BusinessException(ResultCode.NOT_FOUND,"活动不存在");
            }
            // C5：未预热时不能按数据库库存放行（execute 对未预热活动一律拒绝），否则会与
            // execute 的 Redis 库存口径冲突；但"取消/未开始/已结束"属于活动自身终态，DB 即可精确判定，
            // 应优先返回准确原因；仅"活动正处于可参与时间窗内却未预热"才提示 NOT_PREHEATED。
            ActivityCheckResponse response = new ActivityCheckResponse();
            response.setActivityId(activityId);
            response.setUserId(userId);
            Integer dbStatus = activity.getStatus();
            response.setActivityStatus(dbStatus == null ? null : ActivityStatusEnum.fromValue(dbStatus));
            response.setCanJoin(false);
            boolean cancelled = Integer.valueOf(ActivityStatusEnum.CANCELLED.getCode()).equals(dbStatus);
            LocalDateTime now = LocalDateTime.now();
            if (cancelled) {
                response.setReason("ACTIVITY_CANCELLED");
            } else if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
                response.setReason("ACTIVITY_NOT_STARTED");
            } else if (activity.getEndTime() != null && !now.isBefore(activity.getEndTime())) {
                response.setReason("ACTIVITY_ENDED");
            } else {
                response.setReason("ACTIVITY_NOT_PREHEATED");
            }
            return response;
        }
        // 构建响应对象
        ActivityCheckResponse checkResponse = new ActivityCheckResponse();
        checkResponse.setActivityId(activityId);
        checkResponse.setUserId(userId);
        checkResponse.setActivityStatus(activityVO.getStatus());

        // 【复盘】check 与 execute 的开放判定口径曾不一致：execute 以「缓存时间窗动态推导」为准，
        // 而这里却直接按缓存的 status 快照判断（预热写入的是创建时 NOT_STARTED 快照），
        // 导致前端表现为"check 提示未开始 / execute 却能抢成功"的自相矛盾。
        // 修正：与 execute 统一口径——CANCELLED 显式提前拦截；其余以实时时间窗 + 实时库存判定，
        // status 仅作辅助快照，售罄以库存兜底。
        boolean canJoin;
        String reason;
        if (activityVO.getStatus() == ActivityStatusEnum.CANCELLED) {
            canJoin = false;
            reason = "ACTIVITY_CANCELLED";
        } else {
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(activityVO.getStartTime())) {
                canJoin = false;
                reason = "ACTIVITY_NOT_STARTED";
            } else if (!now.isBefore(activityVO.getEndTime())) {
                canJoin = false;
                reason = "ACTIVITY_ENDED";
            } else {
                // 时间窗内：检查实时库存（缓存优先，未预热降级 DB 库存）
                Integer stock = seckillCacheService.getStockFromCache(activityId);
                if (stock == null) {
                    SeckillActivity activity = seckillActivityMapper.selectById(activityId);
                    stock = activity != null ? activity.getSeckillStock() : 0;
                }
                if (stock != null && stock > 0) {
                    canJoin = true;
                    reason = "ALLOW";
                } else {
                    canJoin = false;
                    reason = "ACTIVITY_SOLD_OUT";
                }
            }
        }
        checkResponse.setCanJoin(canJoin);
        checkResponse.setReason(reason);
        return checkResponse;
    }

    private void validateActivityRequest(ActivityRequest request) {
        if (request.getEndTime() != null && request.getStartTime() != null
                && !request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        if (request.getLimitPerUser() != null && request.getLimitPerUser() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前系统仅支持每人限购 1 件");
        }
        if (request.getProductId() != null && productMapper.selectById(request.getProductId()) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关联商品不存在，productId=" + request.getProductId());
        }
    }
}
