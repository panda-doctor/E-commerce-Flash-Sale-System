package com.ghb.ecommerceflashsalesystem.ai.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ai 客服的「商品 / 活动目录」查询端口（P4 Day 5）。
 *
 * <p><b>为什么新增这个端口</b>：ai 过去直读 DB（{@code ProductMapper} / {@code SeckillActivityMapper}），
 * 这让 P2 Day 3 定下的"ai 无库"名不副实，也把 ai 钉在旧库上（旧库下线路上的一块）。
 * 本端口把这份读取收敛成一个出口，实现改为调用**服务公开读接口**
 * （{@code flash-sale-product} 的 {@code GET /api/products}、{@code flash-sale-seckill} 的
 * {@code GET /api/seckill/activities}）—— ai 不再需要数据源。</p>
 *
 * <p><b>端口放消费方模块</b>：与 {@code analytics.OrderQueryPort} 同一约定（谁需要谁定义）。
 * 只有 ai 需要这个端口，所以实现（HTTP 适配器）也留在 ai 内 —— 不像
 * {@code activity.port.ProductCatalogPort} 那样由 {@code flash-sale-remote} 共享给多个应用。</p>
 *
 * <p><b>失败语义</b>：下游不可用 / 超时 / 响应码异常一律**抛异常**。调用方
 * {@code AiChatServiceImpl} 已有"目录失败降级为空目录、不影响对话主链路"的保护；
 * 这里吞异常只会把故障藏起来。</p>
 */
public interface CatalogQueryPort {

    /**
     * 在售商品（口径与商品服务公开列表一致：只含 {@code ON_SHELF}）。
     *
     * @param limit 最多返回条数（控制 prompt token 开销）
     */
    List<ProductCard> onShelfProducts(int limit);

    /**
     * 进行中 / 未开始且**未结束**的秒杀活动（广场列表口径 + 按时间窗过滤）。
     *
     * <p>过滤放在实现侧：DB 的 {@code status} 是创建时快照、不会自动翻转（M7），
     * 因此必须按 {@code endTime} 排除已过期场次，否则历史活动会混进目录、误导模型。</p>
     */
    List<ActivityCard> activeActivities(int limit);

    /** 商品卡片（目录展示所需的最小字段集） */
    record ProductCard(Long productId, String name, Long originalPrice, Integer availableStock) {
    }

    /**
     * 活动卡片。
     *
     * <p>不带 {@code status}：状态文案由调用方按**时间窗**推导（与 execute / check 口径一致），
     * 不用 DB 快照。</p>
     */
    record ActivityCard(Long activityId, String activityName, Long seckillPrice,
                        LocalDateTime startTime, LocalDateTime endTime) {
    }
}
