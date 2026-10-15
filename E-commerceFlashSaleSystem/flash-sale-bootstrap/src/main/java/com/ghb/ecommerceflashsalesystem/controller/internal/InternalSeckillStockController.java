package com.ghb.ecommerceflashsalesystem.controller.internal;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.contracts.internal.SeckillStockRequest;
import com.ghb.ecommerceflashsalesystem.seckill.service.SeckillStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部接口：写入秒杀活动库存（P3 Day 5 库存权属收口）。
 *
 * <p>调用方是 activity 服务（预热 / 重置库存时），被调方是本单体的 seckill 域 ——
 * {@code seckill:stock:*} 的数据所有者。业务守卫（活动是否存在、是否进行中、时间窗）
 * 由 activity 侧判断，本接口只负责"把给定值写进库存键并设置 TTL"。</p>
 *
 * <p>访问控制：infra 的 {@code InternalAccessWebConfig}（{@code X-Internal-Token}，fail-closed）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/seckill")
@RequiredArgsConstructor
public class InternalSeckillStockController {

    private final SeckillStockService seckillStockService;

    /**
     * POST /internal/seckill/activities/{activityId}/stock
     *
     * @param activityId 活动编号
     * @param request    库存与 TTL
     * @return 写入后的库存
     */
    @PostMapping("/activities/{activityId}/stock")
    public Result<Map<String, Object>> writeStock(@PathVariable Long activityId,
                                                  @RequestBody SeckillStockRequest request) {
        int stock = seckillStockService.writeStock(activityId, request.stock(), request.ttlSeconds());
        log.info("内部接口写入活动库存完成，activityId={}, stock={}", activityId, stock);
        return Result.success(Map.of("activityId", activityId, "stock", stock));
    }
}
