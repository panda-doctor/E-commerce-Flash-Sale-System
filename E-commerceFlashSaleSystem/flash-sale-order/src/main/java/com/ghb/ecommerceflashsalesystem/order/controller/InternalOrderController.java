package com.ghb.ecommerceflashsalesystem.order.controller;

import com.ghb.ecommerceflashsalesystem.analytics.port.OrderQueryPort;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.contracts.internal.OrderNosRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单查询内部接口（P4 Day 8）：给**单体侧**的 analytics 读模型（rank / metrics）用，
 * 替代它直读旧库 flash_sale 里的 order 副本。
 *
 * <p><b>为什么需要</b>：`OrderQueryPort` 的实现类在 order-core 共享库里，行为随宿主的
 * datasource 变化 —— 在 order 应用里它是"查权威库"（正确），在单体里就变成"查旧库副本"
 * （P4 三阶段迁移要消灭的最后一条读边）。本接口让订单数据**只有一个出口**：order 服务。</p>
 *
 * <p><b>访问控制</b>：由 infra 的 {@code InternalAccessWebConfig} 统一负责
 * （{@code X-Internal-Token}，fail-closed），且网关只路由 {@code /api/**} → 不经公网暴露。</p>
 *
 * <p>实现委托 {@link OrderQueryPort}（本应用里是查自己权威库的本地实现），因此这里没有重复逻辑。</p>
 */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderQueryPort orderQueryPort;

    /** POST /internal/orders/order-nos：榜单补订单号 */
    @PostMapping("/order-nos")
    public Result<Map<Long, String>> orderNos(@RequestBody OrderNosRequest request) {
        return Result.success(orderQueryPort.orderNosOf(request.activityId(), request.userIds()));
    }

    /** GET /internal/orders/activities/{activityId}/count：活动订单数 */
    @GetMapping("/activities/{activityId}/count")
    public Result<Long> countByActivity(@PathVariable Long activityId) {
        return Result.success(orderQueryPort.countByActivity(activityId));
    }
}
