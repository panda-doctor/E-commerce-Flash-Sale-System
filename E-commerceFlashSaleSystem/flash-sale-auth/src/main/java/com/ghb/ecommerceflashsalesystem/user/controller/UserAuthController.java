package com.ghb.ecommerceflashsalesystem.user.controller;


import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.user.dto.request.LoginRequest;
import com.ghb.ecommerceflashsalesystem.user.dto.request.RegisterRequest;
import com.ghb.ecommerceflashsalesystem.user.dto.response.AuthResponse;
import com.ghb.ecommerceflashsalesystem.user.dto.response.UserProfileResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import com.ghb.ecommerceflashsalesystem.user.service.UserAccountService;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService.AuthenticatedSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class UserAuthController {
    private final UserAccountService userAccountService;
    private final UserSessionService userSessionService;

    // ---- 审查整改 P2-4：登录防暴破（失败计数 + 短期封禁） ----
    // 放在控制器而不是 UserAccountService：后者在 auth-core，故意不依赖 Redis；
    // 这里是"边缘策略"（限流/封禁），属于应用层职责，且登录入口唯一。
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    /** 触发封禁的连续失败次数（默认 5）。 */
    @org.springframework.beans.factory.annotation.Value("${module.security.login-max-fails:5}")
    private int loginMaxFails = 5;

    /** 失败计数窗口 = 封禁时长（默认 15 分钟）。 */
    @org.springframework.beans.factory.annotation.Value("${module.security.login-fail-window:15m}")
    private java.time.Duration loginFailWindow = java.time.Duration.ofMinutes(15);

    private String loginFailKey(String accountName) {
        return "seckill:login:fail:" + (accountName == null ? "" : accountName.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private long loginFailCount(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value instanceof Number number ? number.longValue() : 0L;
        } catch (Exception e) {
            // 计数读不到时**不放行也不封禁**：交给正常认证流程（Redis 故障不应导致登录不可用）
            return 0L;
        }
    }

    private void countLoginFail(String key) {
        try {
            Long fails = redisTemplate.opsForValue().increment(key);
            if (fails != null && fails == 1L) {
                redisTemplate.expire(key, loginFailWindow);
            }
        } catch (Exception e) {
            log.warn("登录失败计数写入失败（Redis 可能不可用），key={}", key, e);
        }
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        UserAccount user = userAccountService.register(request);
        String csrfToken = userSessionService.create(user, response);
        return Result.success(new AuthResponse(UserProfileResponse.from(user), csrfToken));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        // P2-4：先看是否已被封禁（窗口内连续失败达到阈值）
        String failKey = loginFailKey(request.getAccountName());
        long fails = loginFailCount(failKey);
        if (fails >= loginMaxFails) {
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    "登录失败次数过多，请 " + loginFailWindow.toMinutes() + " 分钟后再试");
        }
        UserAccount user;
        try {
            user = userAccountService.authenticate(request.getAccountName(), request.getPassword());
        } catch (BusinessException e) {
            countLoginFail(failKey);
            throw e;
        }
        // 成功即清零，避免"历史失败"影响后续登录
        try {
            redisTemplate.delete(failKey);
        } catch (Exception e) {
            log.warn("登录成功后清理失败计数异常，key={}", failKey, e);
        }
        String csrfToken = userSessionService.create(user, response);
        return Result.success(new AuthResponse(UserProfileResponse.from(user), csrfToken));
    }

    @GetMapping("/me")
    public Result<UserProfileResponse> me(HttpServletRequest request) {
        Long userId = authenticatedUserId(request);
        return Result.success(userAccountService.profile(userId));
    }

    @GetMapping("/csrf")
    public Result<String> csrf(HttpServletRequest request, HttpServletResponse response) {
        Object value = request.getAttribute(UserSessionService.AUTH_SESSION_ATTRIBUTE);
        if (!(value instanceof AuthenticatedSession session)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return Result.success(userSessionService.rotateCsrf(session, response));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        userSessionService.revoke(request, response);
        return Result.success(null);
    }

    private Long authenticatedUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(UserSessionService.AUTHENTICATED_USER_ID);
        if (!(userId instanceof Long id)) throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        return id;
    }
}
