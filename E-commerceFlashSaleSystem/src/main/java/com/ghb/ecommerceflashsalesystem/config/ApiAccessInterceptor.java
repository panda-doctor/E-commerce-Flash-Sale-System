package com.ghb.ecommerceflashsalesystem.config;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 访问令牌校验（审计整改 R3 引入；动态发令牌增强：用户身份源 = 静态白名单 ∪ 动态注册表）。
 *
 * <p>本类不注册为 Spring MVC 拦截器，而是由 {@link ApiAccessWebConfig} 以匿名 HandlerInterceptor
 * 对指定路径包装调用 requireAdmin / requireUser，两类路径互不干扰。拦截范围：
 * {@code /api/admin/**}（管理令牌）、{@code execute / check / users/{userId}/orders}（用户令牌）。
 *
 * <ul>
 *   <li>管理端：请求头 {@code X-Admin-Token} 必须等于配置的管理令牌，否则 40100；</li>
 *   <li>用户端：请求头 {@code X-User-Token} 须能在 {@link UserTokenRegistry}
 *       （静态白名单或动态注册表）中解析出 userId，成功后将 uid 写入 request attribute
 *       {@value #AUTHENTICATED_USER_ID}，供业务 Controller 与请求体中的 userId 比对；
 *       解析失败抛 40100「用户访问令牌无效」。</li>
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

    public boolean requireAdmin(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
        String token = request.getHeader(USER_TOKEN_HEADER);
        Long userId = userTokenRegistry.resolveUserId(token).orElse(null);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户访问令牌无效");
        }
        request.setAttribute(AUTHENTICATED_USER_ID, userId);
        return true;
    }
}
