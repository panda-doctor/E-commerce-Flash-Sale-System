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
 * 内部接口访问控制（P3 Day 5）：校验 {@code /internal/**} 的 {@code X-Internal-Token}。
 *
 * <p>放在 {@code infra} 是"安全默认值"的设计：任何服务将来新增 {@code /internal/**} 端点，
 * 都自动获得令牌校验，不需要每个开发者记得加 —— 忘了配置令牌只会导致**拒绝**（fail-closed），
 * 而不是静默放行。</p>
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(InternalTokenProperties.class)
public class InternalAccessWebConfig implements WebMvcConfigurer {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final InternalTokenProperties internalTokenProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                String configured = internalTokenProperties.getToken();
                if (configured == null || configured.isBlank()) {
                    throw new BusinessException(ResultCode.UNAUTHORIZED, "内部接口令牌未配置");
                }
                if (!configured.equals(request.getHeader(INTERNAL_TOKEN_HEADER))) {
                    throw new BusinessException(ResultCode.UNAUTHORIZED, "内部接口令牌无效");
                }
                return true;
            }
        }).addPathPatterns("/internal/**");
    }
}
