package com.ghb.ecommerceflashsalesystem.user.controller;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.contracts.auth.SessionValidationPort;
import com.ghb.ecommerceflashsalesystem.contracts.internal.SessionValidationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话校验内部接口（P4 Day 7）：给 monolith / seckill / order 三个运行时的会话守卫用，
 * 替代它们直连 auth 的两张表（那是 order 双数据源桥与 seckill 应用"整库指旧库"的根因）。
 *
 * <p><b>访问控制</b>：由 infra 的 {@code InternalAccessWebConfig} 统一负责
 * （{@code X-Internal-Token}，fail-closed，未配令牌时一律拒绝）—— 本接口不经网关暴露。</p>
 *
 * <p><b>响应语义</b>：{@code code=0 且 data=null} = 会话无效（调用方按"未登录"处理）；
 * {@code data={userId, csrfTokenHash}} = 有效。调用方（{@link SessionValidationPort} 的 HTTP 实现）
 * 只把非 0 业务码 / 传输异常当作"校验动作失败"并 fail-closed。</p>
 *
 * <p>本接口委托 {@link SessionValidationPort}：在 auth 应用里生效的是查库的本地实现
 * （{@code module.auth.mode=db}），所以这里没有重复的校验逻辑。</p>
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalSessionController {

    private final SessionValidationPort sessionValidationPort;

    /** POST /internal/auth/sessions/validate */
    @PostMapping("/sessions/validate")
    public Result<SessionValidationPort.SessionView> validate(@RequestBody SessionValidationRequest request) {
        SessionValidationPort.SessionView view =
                sessionValidationPort.validate(request.tokenHash()).orElse(null);
        return Result.success(view);
    }
}
