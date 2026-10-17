package com.ghb.ecommerceflashsalesystem.user.service;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.user.config.SecurityProperties;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserSession;
import com.ghb.ecommerceflashsalesystem.user.mapper.UserSessionMapper;
// P4 收官清理：反向镜像已随旧库下线退役
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "module.auth", name = "mode", havingValue = "db")
public class UserSessionService {
    public static final String AUTH_SESSION_ATTRIBUTE = "authenticatedSession";
    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    /** CSRF cookie 名（P4 Day 7：SessionGuard 也要比对它，故放开可见性） */
    public static final String CSRF_COOKIE = "FLASH_CSRF";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSessionMapper sessionMapper;
    private final UserAccountService userAccountService;
    private final SecurityProperties securityProperties;
    /** P4 Day 6：auth 权威库在 flash_sale_auth，会话写入后镜像到旧库副本供另外三个运行时做会话校验 */
    // P4 收官清理：authMirror 字段与 4 处镜像调用已删除（旧库已 DROP）

    public String create(UserAccount user, HttpServletResponse response) {
        String token = randomToken();
        String csrf = randomToken();
        LocalDateTime now = LocalDateTime.now();
        UserSession session = new UserSession();
        session.setTokenHash(sha256(token));
        session.setCsrfTokenHash(sha256(csrf));
        session.setUserId(user.getId());
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusHours(securityProperties.getSessionTtlHours()));
        sessionMapper.insert(session);

        addCookie(response, securityProperties.getSessionCookieName(), token, true,
                securityProperties.getSessionTtlHours() * 3600L);
        addCookie(response, CSRF_COOKIE, csrf, false, securityProperties.getSessionTtlHours() * 3600L);
        return csrf;
    }

    public AuthenticatedSession authenticate(HttpServletRequest request) {
        String token = cookieValue(request, securityProperties.getSessionCookieName());
        if (token == null || token.isBlank()) return null;
        String hash = sha256(token);
        UserSession session = sessionMapper.findActive(hash, LocalDateTime.now());
        if (session == null) return null;
        userAccountService.getActive(session.getUserId());
        return new AuthenticatedSession(session.getUserId(), hash, session.getCsrfTokenHash());
    }

    public String rotateCsrf(AuthenticatedSession session, HttpServletResponse response) {
        String csrf = randomToken();
        String csrfHash = sha256(csrf);
        LocalDateTime now = LocalDateTime.now();
        if (sessionMapper.rotateCsrf(session.getTokenHash(), csrfHash, now) != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录会话已失效");
        }

        session.setCsrfTokenHash(csrfHash);
        addCookie(response, CSRF_COOKIE, csrf, false, securityProperties.getSessionTtlHours() * 3600L);
        return csrf;
    }

    public void requireCsrf(HttpServletRequest request, AuthenticatedSession session) {
        String header = request.getHeader(CSRF_HEADER);
        String cookie = cookieValue(request, CSRF_COOKIE);
        if (header == null || cookie == null || !MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),
                cookie.getBytes(StandardCharsets.UTF_8)) || !MessageDigest.isEqual(
                sha256(header).getBytes(StandardCharsets.UTF_8), session.getCsrfTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ResultCode.CSRF_INVALID, "安全校验已失效，请刷新后重试");
        }
    }

    public void revoke(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieValue(request, securityProperties.getSessionCookieName());
        if (token != null && !token.isBlank()) {
            String tokenHash = sha256(token);
            LocalDateTime now = LocalDateTime.now();
            sessionMapper.revoke(tokenHash, now);

        }
        addCookie(response, securityProperties.getSessionCookieName(), "", true, 0);
        addCookie(response, CSRF_COOKIE, "", false, 0);
    }

    /**
     * 清理过期/已吊销会话（权威 + 副本两侧）。
     *
     * <p><b>P4 Day 6 起：调度入口在 auth 应用</b>（{@code flash-sale-auth} 的
     * {@code user.scheduler.AuthSessionCleanupScheduler}），本方法**刻意不带 {@code @Scheduled}**
     * —— 这个类活在 auth-core 共享库里，而带 {@code @EnableScheduling} 且宿主 auth-core 的
     * 运行时有一共三个（monolith / order / seckill），注解留在这里会让**三个运行时各自 DELETTE 一次**
     * （今天都指旧库、幂等所以无害；权威切到 flash_sale_auth 之后就变成"非属主去删副本"）。
     * 归属用"文件位置"表达，与 Day 3 的 {@code StreamConsumerScheduler} 同一手法。</p>
     *
     * @return 权威库删除的行数（观测用）
     */
    public int purgeExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = sessionMapper.deleteExpired(now);
        /* P4 收官清理：副本侧 purgeExpired 镜像已随旧库下线移除 */ // 副本侧同步清理，否则旧库过期行长期堆积、对账漂移
        return deleted;
    }

    private void addCookie(HttpServletResponse response, String name, String value, boolean httpOnly, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(securityProperties.isSessionCookieSecure())
                .sameSite("Strict")
                .path("/api")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    @Data
    @AllArgsConstructor
    public static class AuthenticatedSession {
        private Long userId;
        private String tokenHash;
        private String csrfTokenHash;
    }
}
