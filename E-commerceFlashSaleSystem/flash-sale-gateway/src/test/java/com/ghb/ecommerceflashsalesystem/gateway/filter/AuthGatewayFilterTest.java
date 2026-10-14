package com.ghb.ecommerceflashsalesystem.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关准入规则的纯单测（P2 Day 4）——不启动网关、不连注册中心/Nacos，
 * 用 {@code MockServerWebExchange} 直接驱动过滤器，验证"放行 / 拒绝"两类判定。
 */
class AuthGatewayFilterTest {

    private static final String ADMIN = "test-admin-token";

    /** 记录链是否被调用：未调用即表示请求被网关拒绝。 */
    private static final class RecordingChain implements GatewayFilterChain {
        private boolean invoked;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            invoked = true;
            return Mono.empty();
        }
    }

    private final AuthGatewayFilter filter = new AuthGatewayFilter(ADMIN, "FLASH_SESSION");

    @Test
    void preflightOptionsIsForwarded() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/admin/users").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void adminRequestWithoutTokenIsRejectedByGateway() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/seckill/activities").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("40100").contains("管理端访问令牌无效");
    }

    @Test
    void adminRequestWithWrongTokenIsRejected() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users").header("X-Admin-Token", "wrong").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("40100");
    }

    @Test
    void adminRequestWithCorrectTokenIsForwarded() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users").header("X-Admin-Token", ADMIN).build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void adminRequestIsRejectedWhenTokenNotConfigured() {
        AuthGatewayFilter unconfigured = new AuthGatewayFilter("", "FLASH_SESSION");
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users").header("X-Admin-Token", ADMIN).build());

        unconfigured.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("未配置");
    }

    @Test
    void userProtectedPathWithoutCredentialIsRejected() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/seckill/execute").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("40100").contains("请先登录");
    }

    @Test
    void userProtectedPathWithSessionCookieIsForwarded() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/seckill/execute").cookie(new HttpCookie("FLASH_SESSION", "abc")).build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void userProtectedPathWithLegacyUserTokenIsForwarded() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/seckill/execute").header("X-User-Token", "user-token-a").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void publicPathIsNotAffected() {
        RecordingChain chain = new RecordingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products?page=1&pageSize=3").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
    }
}
