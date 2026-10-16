package com.ghb.ecommerceflashsalesystem.user.web;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.contracts.auth.SessionValidationPort;
import com.ghb.ecommerceflashsalesystem.user.config.SecurityProperties;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService.AuthenticatedSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 会话守卫（P4 Day 7）：拦截器**唯一**的会话依赖，本身不含任何 mapper。
 *
 * <p><b>为什么要它</b>：Day 7 之前拦截器直接注入 {@code UserSessionService}（auth-core 的
 * 共享服务，构造要 {@code UserSessionMapper}）→ 于是 monolith / seckill / order 三个运行时都
 * 必须能连 auth 的表，order 为此背了双数据源桥、seckill 应用整库指旧库。现在把"怎么校验"
 * 收敛到 {@link SessionValidationPort}（db 模式查库 / 默认 remote 走内部接口），守卫只做
 * cookie 解析 + CSRF 比对 —— 于是另外三个运行时彻底不需要 auth 的 mapper。</p>
 *
 * <p><b>两条纪律</b>：① <b>无 Cookie 立即返回 null</b>（短路）—— 不能让每个未登录请求都打一次
 * 跨进程校验；② CSRF 比对用的是**校验结果里的哈希**（远端返回），所以 CSRF 语义跨进程保持不变。</p>
 */
@Component
@RequiredArgsConstructor
public class SessionGuard {

    private final SessionValidationPort sessionValidationPort;
    private final SecurityProperties securityProperties;

    /**
     * 解析并校验会话 Cookie。
     *
     * @return 有效会话；无 cookie / 无效 / 已吊销 → {@code null}
     * @throws BusinessException 校验动作本身失败（下游不可用等，fail-closed，绝不静默放行）
     */
    public AuthenticatedSession authenticate(HttpServletRequest request) {
        String token = cookieValue(request, securityProperties.getSessionCookieName());
        if (token == null || token.isBlank()) {
            return null;
        }
        String hash = sha256(token);
        Optional<SessionValidationPort.SessionView> view = sessionValidationPort.validate(hash);
        return view.map(v -> new AuthenticatedSession(v.userId(), hash, v.csrfTokenHash())).orElse(null);
    }

    /** 写请求的防跨站校验（与 Day 7 之前同语义：header 与 cookie 必须一致，且哈希等于会话里的） */
    public void requireCsrf(HttpServletRequest request, AuthenticatedSession session) {
        String header = request.getHeader(UserSessionService.CSRF_HEADER);
        String cookie = cookieValue(request, UserSessionService.CSRF_COOKIE);
        if (header == null || cookie == null
                || !MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),
                        cookie.getBytes(StandardCharsets.UTF_8))
                || !MessageDigest.isEqual(sha256(header).getBytes(StandardCharsets.UTF_8),
                        session.getCsrfTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ResultCode.CSRF_INVALID, "安全校验已失效，请刷新后重试");
        }
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
