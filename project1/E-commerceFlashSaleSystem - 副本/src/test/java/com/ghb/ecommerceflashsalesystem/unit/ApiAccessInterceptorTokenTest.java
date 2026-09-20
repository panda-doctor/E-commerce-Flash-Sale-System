package com.ghb.ecommerceflashsalesystem.unit;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.config.ApiAccessInterceptor;
import com.ghb.ecommerceflashsalesystem.config.SecurityProperties;
import com.ghb.ecommerceflashsalesystem.config.UserTokenRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R3 访问拦截器「用户令牌校验」单元测试（mock Servlet，不启动 Spring）。
 *
 * <p>验证用户身份源 = 静态白名单 ∪ 动态注册表：两类令牌都能通过 requireUser 并写入
 * authenticatedUserId；缺失 / 未知令牌一律抛 40100。
 */
public class ApiAccessInterceptorTokenTest {

    private SecurityProperties props;
    private UserTokenRegistry registry;
    private ApiAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        props = new SecurityProperties();
        props.setUserTokens("user-a:1001");
        registry = new UserTokenRegistry(props);
        interceptor = new ApiAccessInterceptor(registry, props);
    }

    private HttpServletRequest requestWithToken(String token) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Token")).thenReturn(token);
        return request;
    }

    @Test
    void staticTokenPassesAndCarriesUid() {
        HttpServletRequest request = requestWithToken("user-a");
        assertThat(interceptor.requireUser(request, mock(HttpServletResponse.class), new Object())).isTrue();
        verify(request).setAttribute(ApiAccessInterceptor.AUTHENTICATED_USER_ID, 1001L);
    }

    @Test
    void dynamicTokenPassesAndCarriesUid() {
        String token = registry.register(9001L).orElseThrow();
        HttpServletRequest request = requestWithToken(token);
        assertThat(interceptor.requireUser(request, mock(HttpServletResponse.class), new Object())).isTrue();
        verify(request).setAttribute(ApiAccessInterceptor.AUTHENTICATED_USER_ID, 9001L);
    }

    @Test
    void missingTokenRejectedWithUnauthorized() {
        HttpServletRequest request = requestWithToken(null);
        assertThatThrownBy(() ->
                interceptor.requireUser(request, mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void unknownTokenRejectedWithUnauthorized() {
        HttpServletRequest request = requestWithToken("hacker-token");
        assertThatThrownBy(() ->
                interceptor.requireUser(request, mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
    }
}
