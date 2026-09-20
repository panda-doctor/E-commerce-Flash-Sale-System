package com.ghb.ecommerceflashsalesystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 为管理接口和会产生/查询用户订单的接口安装访问控制。 */
@Configuration
@RequiredArgsConstructor
public class ApiAccessWebConfig implements WebMvcConfigurer {

    private final ApiAccessInterceptor apiAccessInterceptor;

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
                .addPathPatterns("/api/seckill/execute", "/api/seckill/activities/*/check", "/api/seckill/users/*/orders");
    }
}
