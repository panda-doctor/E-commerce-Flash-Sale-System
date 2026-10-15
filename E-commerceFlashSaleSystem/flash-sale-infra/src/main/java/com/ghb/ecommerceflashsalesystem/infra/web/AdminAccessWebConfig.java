package com.ghb.ecommerceflashsalesystem.infra.web;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端访问控制（P3 Day 5）：只做一件事 —— 校验 {@code /api/admin/**} 的 {@code X-Admin-Token}。
 *
 * <p>放在 {@code infra} 而非某个业务域：所有对外服务都依赖 infra，于是"凡是暴露
 * {@code /api/admin/**} 的应用都有服务侧纵深防御"，不再依赖"记得把 auth-core 带进来"。
 * 与 auth-core 的 {@code ApiAccessWebConfig} 相比，本类<b>刻意不含</b>会话 / CSRF / CORS 逻辑
 * （那些属于 auth 域语义，留在 auth-core）。</p>
 *
 * <p>行为与 auth-core 的 {@code requireAdmin} 保持一致：{@code OPTIONS} 预检放行；
 * 未配置令牌或令牌不匹配 → {@code 40100}。</p>
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminTokenProperties.class)
public class AdminAccessWebConfig implements WebMvcConfigurer {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final AdminTokenProperties adminTokenProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                String configured = adminTokenProperties.getAdminToken();
                if (configured == null || configured.isBlank()) {
                    throw new BusinessException(ResultCode.UNAUTHORIZED, "管理端访问令牌未配置");
                }
                if (!configured.equals(request.getHeader(ADMIN_TOKEN_HEADER))) {
                    throw new BusinessException(ResultCode.UNAUTHORIZED, "管理端访问令牌无效");
                }
                return true;
            }
        }).addPathPatterns("/api/admin/**");
    }
}
