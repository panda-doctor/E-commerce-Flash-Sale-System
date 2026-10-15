package com.ghb.ecommerceflashsalesystem.remote.adapter;

import com.ghb.ecommerceflashsalesystem.activity.port.SeckillStockPort;
import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.contracts.internal.SeckillStockRequest;
import com.ghb.ecommerceflashsalesystem.infra.web.InternalTokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * {@link SeckillStockPort} 的 <b>HTTP 适配器</b>（P4 Day 2 起由 flash-sale-remote 提供给需要跨进程调用的应用）。
 *
 * <p>调用单体（或未来 seckill 应用）的内部接口
 * {@code POST /internal/seckill/activities/{id}/stock}（{@code lb://flash-sale-monolith}，不经网关），
 * 随请求带 {@code X-Internal-Token}。</p>
 *
 * <p><b>失败语义</b>：任何失败（网络 / 令牌被拒 / 业务码非 0）都抛异常，
 * 由调用方（{@code SeckillCacheService}）转成"预热失败"状态 —— 绝不允许"静默当成成功"，
 * 否则会出现"DB 说已预热、Redis 里没有库存"的假预热。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpSeckillStockAdapter implements SeckillStockPort {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String STOCK_URI =
            "http://flash-sale-monolith/internal/seckill/activities/{activityId}/stock";

    private final RestClient internalServiceRestClient;
    private final InternalTokenProperties internalTokenProperties;

    @Override
    public int writeStock(Long activityId, Integer configuredStock, long ttlSeconds) {
        try {
            Result<Map<String, Object>> response = internalServiceRestClient.post()
                    .uri(STOCK_URI, activityId)
                    .header(INTERNAL_TOKEN_HEADER, String.valueOf(internalTokenProperties.getToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SeckillStockRequest(configuredStock, ttlSeconds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()) {
                String message = response == null ? "空响应" : response.getMessage();
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "写入秒杀库存失败：" + message);
            }
            return configuredStock;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 seckill 内部库存接口失败，activityId={}", activityId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "写入秒杀库存失败（内部接口异常）：" + e.getMessage());
        }
    }
}
