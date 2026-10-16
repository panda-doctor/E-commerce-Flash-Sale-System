package com.ghb.ecommerceflashsalesystem.order.controller;

import com.ghb.ecommerceflashsalesystem.analytics.service.MetricsService;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标快照落库端点（P4 Day 10 · Step B 第二半）：**归属搬到 order 应用**。
 *
 * <p><b>为什么搬</b>：快照表 {@code seckill_activity_snapshot} 的权威库已定为
 * {@code flash_sale_order}（Day 10 数据面第一半已建同构表 ✓）—— 谁拥有表，谁承载写端点。
 * 端点原先在 analytics 模块的 {@code AdminMetricsController} 里，而那个类被**单体与 order
 * 同时扫到**，于是"写旧库快照"的能力出现在单体里；本日把写路径按"**文件位置表达归属**"
 * （Day 3 调度器 / Day 6 清理任务同一手法）移到 order 应用，单体侧只保留**读**端点
 * （{@code GET /{id}/metrics} 仍由 analytics 的控制器提供，两个宿主都在跑，无害）。</p>
 *
 * <p>路径与网关：{@code POST /api/admin/seckill/activities/{id}/snapshot} 走管理端护栏
 * （管理令牌）。网关侧已为 order 域补一条**精确** Path —— 否则会落到 monolith 的
 * {@code /api/**} 兜底路由上（那条兜底排最后，先匹配先赢）。</p>
 */
@RestController
@RequestMapping("/api/admin/seckill/activities")
@RequiredArgsConstructor
public class AdminMetricsCaptureController {

    private final MetricsService metricsService;

    /** 落库一份活动指标快照（接口 4.12 的快照动作） */
    @PostMapping("/{activityId}/snapshot")
    public Result<Void> captureSnapshot(@PathVariable Long activityId) {
        metricsService.captureSnapshot(activityId);
        return Result.success();
    }
}
