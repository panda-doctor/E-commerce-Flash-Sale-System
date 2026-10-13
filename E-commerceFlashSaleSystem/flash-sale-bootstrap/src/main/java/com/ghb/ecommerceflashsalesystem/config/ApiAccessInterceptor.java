package com.ghb.ecommerceflashsalesystem.config;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.user.config.SecurityProperties;
import com.ghb.ecommerceflashsalesystem.user.config.UserTokenRegistry;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService.AuthenticatedSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 管理令牌、持久化浏览器会话和可选压测令牌校验。
 *
 * <p>本类不注册为 Spring MVC 拦截器，而是由 {@link ApiAccessWebConfig} 以匿名 HandlerInterceptor
 * 对指定路径包装调用 requireAdmin / requireUser。管理接口校验管理令牌；用户业务优先校验
 * 数据库会话 Cookie，并检查写请求的防跨站令牌。
 *
 * <ul>
 *   <li>管理端：请求头 {@code X-Admin-Token} 必须等于配置的管理令牌，否则 40100；</li>
 *   <li>用户端：从会话解析 userId 并写入 request attribute {@value #AUTHENTICATED_USER_ID}；
 *       旧 {@code X-User-Token} 仅在显式打开兼容配置时接受。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ApiAccessInterceptor {

    /** 鉴权通过后写入 request attribute 的 userId 键名，业务 Controller 据此与请求参数比对 */
    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String USER_TOKEN_HEADER = "X-User-Token";

    private final UserTokenRegistry userTokenRegistry;
    private final SecurityProperties securityProperties;
    private final UserSessionService userSessionService;

    public boolean requireAdmin(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String configured = securityProperties.getAdminToken();
        if (configured == null || configured.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "管理端访问令牌未配置");
        }
        if (!configured.equals(request.getHeader(ADMIN_TOKEN_HEADER))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "管理端访问令牌无效");
        }
        return true;
    }

    public boolean requireUser(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        AuthenticatedSession session = userSessionService.authenticate(request);
        if (session != null) {
            if (isMutation(request.getMethod())) userSessionService.requireCsrf(request, session);
            request.setAttribute(AUTHENTICATED_USER_ID, session.getUserId());
            request.setAttribute(UserSessionService.AUTH_SESSION_ATTRIBUTE, session);
            return true;
        }
        if (!securityProperties.isLegacyUserTokensEnabled()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String token = request.getHeader(USER_TOKEN_HEADER);
        Long userId = userTokenRegistry.resolveUserId(token).orElse(null);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户访问令牌无效");
        }
        request.setAttribute(AUTHENTICATED_USER_ID, userId);
        return true;
    }

    public boolean requireSessionUser(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        AuthenticatedSession session = userSessionService.authenticate(request);
        if (session == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        if (isMutation(request.getMethod())) userSessionService.requireCsrf(request, session);
        request.setAttribute(AUTHENTICATED_USER_ID, session.getUserId());
        request.setAttribute(UserSessionService.AUTH_SESSION_ATTRIBUTE, session);
        return true;
    }

    private boolean isMutation(String method) {
        return !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method);
    }
}
