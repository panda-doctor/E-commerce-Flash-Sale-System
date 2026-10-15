package com.ghb.ecommerceflashsalesystem.activity.adapter;

import com.ghb.ecommerceflashsalesystem.activity.port.ProductCatalogPort;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.contracts.internal.ProductBriefView;
import com.ghb.ecommerceflashsalesystem.contracts.internal.ProductBriefsRequest;
import com.ghb.ecommerceflashsalesystem.infra.web.InternalTokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ProductCatalogPort} 的 <b>HTTP 适配器</b>（activity 应用专用）。
 *
 * <p>调用商品应用的内部批量接口 {@code POST /internal/products/briefs}
 * （{@code lb://flash-sale-product}，不经网关，带 {@code X-Internal-Token}）——
 * 用批量接口而不是"逐个查详情"，避免活动广场列表出现 N+1 次 HTTP 调用。</p>
 *
 * <p><b>失败语义</b>：下游不可用/令牌被拒/业务码非 0 一律抛异常。
 * {@link #exists(Long)} 尤其不能把"下游挂了"返回成 {@code false} ——
 * 那会让"创建活动"误报"商品不存在"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpProductCatalogAdapter implements ProductCatalogPort {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String BRIEFS_URI = "http://flash-sale-product/internal/products/briefs";

    private final RestClient internalServiceRestClient;
    private final InternalTokenProperties internalTokenProperties;

    @Override
    public boolean exists(Long productId) {
        if (productId == null) {
            return false;
        }
        return findBriefs(List.of(productId)).containsKey(productId);
    }

    @Override
    public Map<Long, ProductBrief> findBriefs(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = productIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        try {
            Result<List<ProductBriefView>> response = internalServiceRestClient.post()
                    .uri(BRIEFS_URI)
                    .header(INTERNAL_TOKEN_HEADER, String.valueOf(internalTokenProperties.getToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ProductBriefsRequest(distinctIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()
                    || response.getData() == null) {
                String message = response == null ? "空响应" : response.getMessage();
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "查询商品摘要失败：" + message);
            }
            return response.getData().stream()
                    .collect(Collectors.toMap(ProductBriefView::productId, toBrief(), (a, b) -> a));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用商品服务内部接口失败，ids={}", distinctIds, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "查询商品摘要失败（内部接口异常）：" + e.getMessage());
        }
    }

    private Function<ProductBriefView, ProductBrief> toBrief() {
        return v -> new ProductBrief(v.productId(), v.name(), v.imageUrl(), v.originalPrice());
    }
}
