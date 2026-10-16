package com.ghb.ecommerceflashsalesystem.analytics.adapter;

import com.ghb.ecommerceflashsalesystem.analytics.port.OrderQueryPort;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.contracts.internal.OrderNosRequest;
import com.ghb.ecommerceflashsalesystem.infra.web.InternalTokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单查询端口的**跨进程实现**（P4 Day 8）：调 order 服务的内部接口，替代在单体里直读
 * 旧库 flash_sale 的 order 副本 —— 这是三阶段迁移里最后一条**读业务表**的边。
 *
 * <p>只有配了 {@code module.analytics.order-query=remote} 的宿主（即**单体**）才创建本 Bean；
 * order 应用保持默认的本地实现（查自己的权威库）。</p>
 *
 * <p><b>失败语义（与 Day 7 的内部会话校验同一条纪律）</b>：调不通/业务码非 0 **抛错**
 * （fail-closed），绝不返回"0 笔订单"—— 那会让榜单静默变空、指标失真，比报错难查得多。
 * 只有"查得到但结果为空"才是合法的空集合。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "module.analytics", name = "order-query", havingValue = "remote")
@RequiredArgsConstructor
public class HttpOrderQueryAdapter implements OrderQueryPort {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String ORDER_NOS_URI = "http://flash-sale-order/internal/orders/order-nos";
    private static final String COUNT_URI_TEMPLATE =
            "http://flash-sale-order/internal/orders/activities/{activityId}/count";

    private final RestClient internalServiceRestClient;
    private final InternalTokenProperties internalTokenProperties;

    @Override
    public Map<Long, String> orderNosOf(Long activityId, Collection<Long> userIds) {
        if (activityId == null || userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Result<Map<String, String>> response = postForResult(ORDER_NOS_URI,
                new OrderNosRequest(activityId, List.copyOf(userIds)),
                new ParameterizedTypeReference<>() {
                });
        Map<String, String> raw = response.getData();
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        // JSON 对象的 key 只能是字符串 → 这里显式还原成 userId（Long）
        Map<Long, String> converted = new LinkedHashMap<>();
        raw.forEach((key, value) -> converted.put(Long.valueOf(key), value));
        return converted;
    }

    @Override
    public long countByActivity(Long activityId) {
        if (activityId == null) {
            return 0L;
        }
        try {
            Result<Long> response = internalServiceRestClient.get()
                    .uri(COUNT_URI_TEMPLATE, activityId)
                    .header(INTERNAL_TOKEN_HEADER, String.valueOf(internalTokenProperties.getToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR,
                        "订单计数失败：" + (response == null ? "空响应" : response.getMessage()));
            }
            return response.getData() == null ? 0L : response.getData();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 order 内部订单计数接口失败（fail-closed，拒绝本次查询）", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "订单查询服务不可用：" + e.getMessage());
        }
    }

    private <T> Result<T> postForResult(String uri, Object body, ParameterizedTypeReference<Result<T>> type) {
        try {
            Result<T> response = internalServiceRestClient.post()
                    .uri(uri)
                    .header(INTERNAL_TOKEN_HEADER, String.valueOf(internalTokenProperties.getToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR,
                        "榜单订单号查询失败：" + (response == null ? "空响应" : response.getMessage()));
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 order 内部榜单接口失败（fail-closed，拒绝本次查询）", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "订单查询服务不可用：" + e.getMessage());
        }
    }
}
