package com.ghb.ecommerceflashsalesystem.user.web;

import com.ghb.ecommerceflashsalesystem.user.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/** 为管理接口和会产生/查询用户订单的接口安装访问控制。 */
@Configuration
@RequiredArgsConstructor
public class ApiAccessWebConfig implements WebMvcConfigurer {

    private final ApiAccessInterceptor apiAccessInterceptor;
    private final SecurityProperties securityProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                             jakarta.servlet.http.HttpServletResponse response,
                                             Object handler) {
                        return apiAccessInterceptor.requireAdmin(request, response, handler);
                    }
                })
                .addPathPatterns("/api/admin/**");
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                             jakarta.servlet.http.HttpServletResponse response,
                                             Object handler) {
                        return apiAccessInterceptor.requireUser(request, response, handler);
                    }
                })
                .addPathPatterns("/api/seckill/execute", "/api/seckill/activities/*/check",
                        "/api/seckill/users/*/orders", "/api/seckill/users/me/orders", "/api/seckill/orders/*");
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                             jakarta.servlet.http.HttpServletResponse response,
                                             Object handler) {
                        return apiAccessInterceptor.requireSessionUser(request, response, handler);
                    }
                })
                .addPathPatterns("/api/auth/me", "/api/auth/csrf", "/api/auth/logout");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(apiOrigins())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Admin-Token", "X-User-Token", "X-CSRF-Token")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private String[] apiOrigins() {
        return securityProperties.getAllowedOriginList().toArray(String[]::new);
    }
}
