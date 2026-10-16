package com.ghb.ecommerceflashsalesystem.remote.adapter;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.contracts.auth.SessionValidationPort;
import com.ghb.ecommerceflashsalesystem.contracts.internal.SessionValidationRequest;
import com.ghb.ecommerceflashsalesystem.infra.web.InternalTokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * 会话校验的**跨进程实现**（P4 Day 7，默认生效）：调 auth 应用的
 * {@code POST /internal/auth/sessions/validate}（LB 服务名 + {@code X-Internal-Token}，不经网关）。
 *
 * <p>供 monolith / seckill / order 复用 —— 它们因此不再需要 auth 的 mapper（order 的双数据源桥
 * 与 seckill 应用"整库指旧库"随之删除）。auth 应用自己用 {@code LocalSessionValidationAdapter}
 * （{@code module.auth.mode=db}），不会创建本 Bean。</p>
 *
 * <p><b>失败语义（本类最重要的一条）</b>：</p>
 * <ul>
 *   <li>"会话不存在"（业务码 0 且 data 为空）→ 返回 {@link Optional#empty()}，由调用方按"未登录"处理；</li>
 *   <li>"校验动作失败"（网络异常 / 令牌被拒 / 业务码非 0）→ **抛异常**（fail-closed）。</li>
 * </ul>
 * <p>绝不能把后者翻译成 empty 之外的东西、更不能在异常时返回"有效" —— 那等于给全站开认证旁路。
 * 也不把 transport 失败当成 empty：两者在调用方看来都会"拒绝请求"，但日志与错误码必须能区分
 * （empty → 40100 请先登录；异常 → 5xx 依赖不可用），否则排障时会误判成用户没登录。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "module.auth", name = "mode", havingValue = "remote", matchIfMissing = true)
@RequiredArgsConstructor
public class HttpSessionValidationAdapter implements SessionValidationPort {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String VALIDATE_URI =
            "http://flash-sale-auth/internal/auth/sessions/validate";

    private final RestClient internalServiceRestClient;
    private final InternalTokenProperties internalTokenProperties;

    @Override
    public Optional<SessionView> validate(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        try {
            Result<SessionView> response = internalServiceRestClient.post()
                    .uri(VALIDATE_URI)
                    .header(INTERNAL_TOKEN_HEADER, String.valueOf(internalTokenProperties.getToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SessionValidationRequest(tokenHash))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()) {
                String message = response == null ? "空响应" : response.getMessage();
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "会话校验失败：" + message);
            }
            return Optional.ofNullable(response.getData());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 auth 内部会话校验接口失败（fail-closed，拒绝本次请求）", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "会话校验服务不可用：" + e.getMessage());
        }
    }
}
