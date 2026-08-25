package com.ghb.ecommerceflashsalesystem.controller.admin;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.ActivityRequest;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端秒杀活动控制器
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
        // 新建活动默认状态为 NOT_STARTED，但 createActivity 并未返回状态，需查询或直接返回 NOT_STARTED
        // 因为创建时默认 status=0（NOT_STARTED），更新时状态不变，故统一返回枚举名
        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);

        // [原方案注释] 对于创建，始终返回 NOT_STARTED；对于更新，状态可能不同，但接口文档示例为 NOT_STARTED，
        // 实际业务中更新可能改变状态，但当前无需知道状态，可简化始终返回 NOT_STARTED，
        // 或通过查询数据库获取真实状态，但增加开销，暂按默认处理。
        // data.put("status", ActivityStatusEnum.NOT_STARTED.name());

        // [修正方案] 更新场景返回当前真实状态（新建返回 NOT_STARTED，更新返回数据库中的真实状态）
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
        //调用预热
        seckillCacheService.preheatActivity(activityId);
        // 预热成功后，返回缓存键和库存
        String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + activityId;
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        Integer stock = seckillCacheService.getStockFromCache(activityId);

        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("activityKey", activityKey);
        data.put("stockKey", stockKey);
        data.put("stock", stock != null ? stock : 0);

        log.info("活动预热成功，activityId={}", activityId);
        return Result.success(data);
    }
}

