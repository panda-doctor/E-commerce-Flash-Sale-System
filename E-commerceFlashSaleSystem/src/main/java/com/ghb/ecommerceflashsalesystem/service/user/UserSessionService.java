package com.ghb.ecommerceflashsalesystem.service.user;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.config.SecurityProperties;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserSession;
import com.ghb.ecommerceflashsalesystem.mapper.UserSessionMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class UserSessionService {
    public static final String AUTH_SESSION_ATTRIBUTE = "authenticatedSession";
    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String CSRF_COOKIE = "FLASH_CSRF";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSessionMapper sessionMapper;
    private final UserAccountService userAccountService;
    private final SecurityProperties securityProperties;

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
        if (sessionMapper.rotateCsrf(session.getTokenHash(), sha256(csrf), LocalDateTime.now()) != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录会话已失效");
        }
        session.setCsrfTokenHash(sha256(csrf));
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
        if (token != null && !token.isBlank()) sessionMapper.revoke(sha256(token), LocalDateTime.now());
        addCookie(response, securityProperties.getSessionCookieName(), "", true, 0);
        addCookie(response, CSRF_COOKIE, "", false, 0);
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void purgeExpiredSessions() {
        sessionMapper.deleteExpired(LocalDateTime.now());
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
