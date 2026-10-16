package com.ghb.ecommerceflashsalesystem.ai.adapter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ghb.ecommerceflashsalesystem.ai.port.CatalogQueryPort;
import com.ghb.ecommerceflashsalesystem.common.api.PageResponse;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * {@link CatalogQueryPort} 的 HTTP 实现（P4 Day 5）：ai 目录改为调**服务公开读接口**。
 *
 * <ul>
 *   <li>在售商品 → {@code GET http://flash-sale-product/api/products?page=1&pageSize=N}
 *       （公开列表本身就是 ON_SHELF 口径 ✓，不需要客户端再筛状态）；</li>
 *   <li>秒杀活动 → {@code GET http://flash-sale-seckill/api/seckill/activities}（活动广场口径），
 *       再按 {@code endTime} 做客户端过滤 —— DB 的 status 是创建时快照不会自动翻转（M7），
 *       所以必须按时间窗排除已结束场次。</li>
 * </ul>
 *
 * <p><b>为什么用公开端点而不是新增内部端点</b>：这两个接口返回的就是"顾客看到的目录"，
 * ai 需要的正是这份数据；加内部端点只会新增 API 面 + 需要内部令牌。写操作才需要内部通道。</p>
 *
 * <p><b>客户端</b>：复用 {@code flash-sale-remote} 提供的 {@code internalServiceRestClient}
 * （带 {@code @LoadBalanced}，URI 里写服务名，由 Nacos + LoadBalancer 解析，不写死 IP:端口）。</p>
 *
 * <p>⚠️ 时间字段必须带 {@link JsonFormat}：上游 VO 用 {@code yyyy-MM-dd HH:mm:ss} 序列化，
 * 不带注解会按 ISO-8601 解析而抛 {@code DateTimeParseException}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCatalogQueryAdapter implements CatalogQueryPort {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String PRODUCTS_URI =
            "http://flash-sale-product/api/products?page=1&pageSize={pageSize}";
    private static final String ACTIVITIES_URI =
            "http://flash-sale-seckill/api/seckill/activities";

    private final RestClient internalServiceRestClient;

    @Override
    public List<ProductCard> onShelfProducts(int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        Result<PageResponse<ProductRow>> response = internalServiceRestClient.get()
                .uri(PRODUCTS_URI, pageSize)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        PageResponse<ProductRow> page = dataOf(response, "查询在售商品失败");
        List<ProductRow> rows = page.getRecords() == null ? List.of() : page.getRecords();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(r -> r.productId() != null)
                .limit(Math.max(limit, 1))
                .map(r -> new ProductCard(r.productId(), r.name(), r.originalPrice(), r.availableStock()))
                .toList();
    }

    @Override
    public List<ActivityCard> activeActivities(int limit) {
        Result<List<ActivityRow>> response = internalServiceRestClient.get()
                .uri(ACTIVITIES_URI)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        List<ActivityRow> rows = dataOf(response, "查询秒杀活动失败");
        LocalDateTime now = LocalDateTime.now();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(r -> r.activityId() != null && r.endTime() != null)
                .filter(r -> r.status() != ActivityStatusEnum.CANCELLED)
                .filter(r -> !r.endTime().isBefore(now))
                .sorted(Comparator.comparing(ActivityRow::startTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(Math.max(limit, 1))
                .map(r -> new ActivityCard(r.activityId(), r.activityName(), r.seckillPrice(),
                        r.startTime(), r.endTime()))
                .toList();
    }

    private <T> T dataOf(Result<T> response, String action) {
        if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()
                || response.getData() == null) {
            String message = response == null ? "空响应" : response.getMessage();
            throw new BusinessException(ResultCode.SYSTEM_ERROR, action + "：" + message);
        }
        return response.getData();
    }

    /** 商品公开列表行（只声明目录需要的字段；Spring 的 ObjectMapper 默认忽略未知字段） */
    record ProductRow(Long productId, String name, Long originalPrice, Integer availableStock) {
    }

    /**
     * 活动广场列表行。
     *
     * <p>{@code status} 直接绑成 {@link ActivityStatusEnum}（上游序列化枚举名）；
     * 只用来排除 {@code CANCELLED}，展示口径仍按时间窗推导。</p>
     */
    record ActivityRow(Long activityId, String activityName, Long seckillPrice,
                       @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                       @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                       ActivityStatusEnum status) {
    }
}
