package com.ghb.ecommerceflashsalesystem.activity.controller;

import com.ghb.ecommerceflashsalesystem.activity.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.activity.dto.request.ActivityRequest;
import com.ghb.ecommerceflashsalesystem.activity.service.SeckillActivityService;
import com.ghb.ecommerceflashsalesystem.activity.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端秒杀活动控制器（P3 Day 4：随 activity 域从单体迁入 flash-sale-activity 应用 :8085）。
 *
 * <p>本类只承载 <b>activity 域自己的</b>管理端接口：建/改活动、预热、库存重置。同前缀下
 * 不属于本域的接口保持原位、路径不变：</p>
 * <ul>
 *   <li>{@code POST /api/admin/seckill/dead-letters/replay} → 留在单体（order 域的消息补偿，
 *       迁过来会让活动服务反向依赖 order 模块）；</li>
 *   <li>{@code GET /api/admin/seckill/activities/{id}/metrics}、{@code POST .../{id}/snapshot}
 *       → 留在单体（analytics 域的读模型）。</li>
 * </ul>
 *
 * <p>因此网关必须<b>精确导流三条路径</b>，不能用 {@code /api/admin/seckill/**} 整段导流
 * （会把上面两组接口一起抢走）。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/seckill")
public class AdminSeckillController {

    private final SeckillActivityService seckillActivityService;
    private final SeckillCacheService seckillCacheService;

    /**
     * 创建或更新秒杀活动（接口 4.4）
     * POST /api/admin/seckill/activities
     *
     * @param request 活动请求
     * @return 活动ID + 状态
     */
    @PostMapping("/activities")
    public Result<Map<String, Object>> createSeckillActivity(@Valid @RequestBody ActivityRequest request) {
        Long activityId = seckillActivityService.createActivity(request);
        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);

        // 更新场景返回当前真实状态（新建返回 NOT_STARTED，更新返回数据库中的真实状态）：
        // 复用 getActivityDetail 查活动详情（缓存优先，未预热时查库），取其中 status 字段
        SeckillActivityVO detail = seckillActivityService.getActivityDetail(activityId);
        ActivityStatusEnum status = (detail != null && detail.getStatus() != null)
                ? detail.getStatus()
                : ActivityStatusEnum.NOT_STARTED; // 兜底：查不到时按默认未开始处理
        data.put("status", status.name());

        log.info("创建/更新活动成功，activityId={}", activityId);
        return Result.success(data);
    }

    /**
     * 预热秒杀活动（接口 4.5）
     * POST /api/admin/seckill/activities/{activityId}/preheat
     *
     * @param activityId 活动ID
     * @return 活动ID + 缓存键 + 库存
     */
    @PostMapping("/activities/{activityId}/preheat")
    public Result<Map<String, Object>> preheatSeckillActivity(@PathVariable Long activityId) {
        //调用预热（内部含 Redisson 锁：预热锁归属 activity 域）
        seckillCacheService.preheatActivity(activityId);
        // 预热成功后，返回缓存键和库存
        String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + activityId;
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        Integer stock = seckillCacheService.getStockFromCache(activityId);
        if (stock == null || !seckillCacheService.isActivityCacheReady(activityId)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "预热请求已执行，但活动缓存未完整写入，请刷新后重试");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("activityKey", activityKey);
        data.put("stockKey", stockKey);
        data.put("stock", stock);

        log.info("活动预热成功，activityId={}", activityId);
        return Result.success(data);
    }

    /**
     * 重置活动库存（运维/压测复位）
     * POST /api/admin/seckill/activities/{activityId}/reset-stock
     *
     * <p>语义：仅允许「未开始」或「已结束」的活动重置库存，活动进行中由
     * {@link SeckillCacheService#resetStock} 内部守卫拒绝（抛 PARAM_ERROR，防止把
     * Redis 已扣减的库存用 DB 配置库存"复活"）。</p>
     *
     * <p>Day 5 计划：库存的"谁的数据谁写"收口 —— 本接口改为调用 seckill 服务暴露的
     * 内部「库存初始化/重置」接口，activity 侧不再直接写 {@code seckill:stock:*}。</p>
     *
     * @param activityId 活动ID
     * @return activityId + stockKey + 重置后库存
     */
    @PostMapping("/activities/{activityId}/reset-stock")
    public Result<Map<String, Object>> resetSeckillStock(@PathVariable Long activityId) {
        // 复用内部含分布式锁 + 活跃期守卫的重置方法（E1-r）
        seckillCacheService.resetStock(activityId);

        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        Integer stock = seckillCacheService.getStockFromCache(activityId);
        if (stock == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "库存重置请求已执行，但缓存库存读取失败，请刷新后重试");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("stockKey", stockKey);
        data.put("stock", stock);

        log.info("库存重置成功，activityId={}, stock={}", activityId, stock);
        return Result.success(data);
    }
}
