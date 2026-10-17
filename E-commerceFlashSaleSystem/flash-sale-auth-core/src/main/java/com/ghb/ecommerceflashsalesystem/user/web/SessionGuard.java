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

    /**
     * P4 收官后优化①：会话校验结果的**本机短 TTL 缓存**（默认 {@code 0s} = 关闭，行为与之前完全一致）。
     *
     * <p><b>为什么</b>：跨服务化后每个受保护请求都要打一次 auth 的内部校验接口。{@code 1s~2s} 的 TTL
     * 足以把同一页面连续请求（列表 + 详情 + 轮询）的往返压掉大半。</p>
     *
     * <p><b>代价（必须显式接受才启用）</b>：吊销（登出 / 改密 / 禁用）的**生效延迟 ≤ TTL**，且各运行时
     * 各自缓存、互不同步。故默认 0（关闭）；启用请配**短**值。</p>
     *
     * <p><b>只缓存"有效"结果</b>：查无此会话不缓存（刚登录可能马上有效），校验失败必须每次重试
     * （fail-closed，异常不落缓存）。</p>
     */
    @org.springframework.beans.factory.annotation.Value("${module.auth.session-cache-ttl:0s}")
    private java.time.Duration sessionCacheTtl = java.time.Duration.ZERO;

    /** 缓存条目上限（超限整体清空：会话是短生命周期对象，简单策略足够）。 */
    private static final int CACHE_MAX_ENTRIES = 10_000;

    private final java.util.Map<String, CachedView> viewCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedView(SessionValidationPort.SessionView view, long expiresAtNanos) {
    }

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
        Optional<SessionValidationPort.SessionView> view;
        SessionValidationPort.SessionView cached = cachedView(hash);
        if (cached != null) {
            return new AuthenticatedSession(cached.userId(), hash, cached.csrfTokenHash());
        }
        view = sessionValidationPort.validate(hash);
        view.ifPresent(v -> cacheView(hash, v));
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

    private SessionValidationPort.SessionView cachedView(String hash) {
        if (sessionCacheTtl.isZero() || sessionCacheTtl.isNegative()) {
            return null;
        }
        CachedView entry = viewCache.get(hash);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtNanos() <= System.nanoTime()) {
            viewCache.remove(hash, entry);
            return null;
        }
        return entry.view();
    }

    private void cacheView(String hash, SessionValidationPort.SessionView view) {
        if (sessionCacheTtl.isZero() || sessionCacheTtl.isNegative()) {
            return;
        }
        if (viewCache.size() >= CACHE_MAX_ENTRIES) {
            viewCache.clear();
        }
        viewCache.put(hash, new CachedView(view, System.nanoTime() + sessionCacheTtl.toNanos()));
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
