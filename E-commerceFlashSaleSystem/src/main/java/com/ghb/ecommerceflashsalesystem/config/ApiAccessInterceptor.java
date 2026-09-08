package com.ghb.ecommerceflashsalesystem.config;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/** 管理端令牌与用户令牌校验，不再无条件信任调用方传入的 userId。 */
@Component
@RequiredArgsConstructor
public class ApiAccessInterceptor implements HandlerInterceptor {

    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String USER_TOKEN_HEADER = "X-User-Token";

    private final SecurityProperties securityProperties;

    public boolean requireAdmin(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String configured = securityProperties.getAdminToken();
        if (configured == null || configured.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "服务端未配置管理端访问令牌");
        }
        if (!configured.equals(request.getHeader(ADMIN_TOKEN_HEADER))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "管理端访问令牌无效");
        }
        return true;
    }

    public boolean requireUser(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(USER_TOKEN_HEADER);
        Long userId = resolveUserId(token);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户访问令牌无效");
        }
        request.setAttribute(AUTHENTICATED_USER_ID, userId);
        return true;
    }

    private Long resolveUserId(String token) {
        if (token == null || token.isBlank() || securityProperties.getUserTokens() == null) {
            return null;
        }
        return Arrays.stream(securityProperties.getUserTokens().split(","))
                .map(String::trim)
                .map(item -> item.split(":", 2))
                .filter(parts -> parts.length == 2 && parts[0].equals(token))
                .map(parts -> {
                    try {
                        return Long.valueOf(parts[1]);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
    }
}
