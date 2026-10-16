package com.ghb.ecommerceflashsalesystem.user.port;

import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.contracts.auth.SessionValidationPort;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserSession;
import com.ghb.ecommerceflashsalesystem.user.mapper.UserSessionMapper;
import com.ghb.ecommerceflashsalesystem.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 本地（查库）会话校验实现 —— **只有 auth 应用生效**（{@code module.auth.mode=db}）。
 *
 * <p>它就是 Day 7 之前 {@code UserSessionService.authenticate} 里的那段逻辑：查有效会话 +
 * 校验账号仍可用。现在被抽成端口实现，好让"权威"与"校验方"用同一个抽象。</p>
 *
 * <p>默认**不生效**：不配 {@code module.auth.mode=db} 的运行时只有 HTTP 适配器 ——
 * 与反向镜像的 {@code enabled} 同一哲学：能直连 auth 库的能力必须显式声明。</p>
 */
@Component
@ConditionalOnProperty(prefix = "module.auth", name = "mode", havingValue = "db")
@RequiredArgsConstructor
public class LocalSessionValidationAdapter implements SessionValidationPort {

    private final UserSessionMapper sessionMapper;
    private final UserAccountService userAccountService;

    @Override
    public Optional<SessionView> validate(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        UserSession session = sessionMapper.findActive(tokenHash, LocalDateTime.now());
        if (session == null) {
            return Optional.empty();
        }
        try {
            // 账号被禁用/软删 → 会话即刻失效（原来由 authenticate 直接抛出，现在收敛成"无效"）
            userAccountService.getActive(session.getUserId());
        } catch (BusinessException e) {
            return Optional.empty();
        }
        return Optional.of(new SessionView(session.getUserId(), session.getCsrfTokenHash()));
    }
}
