package com.ghb.ecommerceflashsalesystem.gateway.filter;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.util.RequestIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 网关统一鉴权 · 准入层（P2 Day 4）。
 *
 * <p><b>职责边界（分层鉴权）</b>：网关只做「准入」——管理接口全量校验管理令牌、用户态接口校验
 * 凭证是否携带；<b>会话有效性、CSRF、资源归属等细校验仍由各服务完成</b>（服务侧
 * {@code ApiAccessInterceptor} 保留为纵深防御，直连服务不会被绕过）。这样既有统一入口的快速拒绝
 * （不消耗后端资源），又不需要网关跨服务查询会话（introspection / JWT 无状态化属 P3+ 演进方向）。</p>
 *
 * <p><b>响应形态</b>：拒绝时由网关直接返回 {@code HTTP 200 + body.code=40100}，与单体
 * {@code GlobalExceptionHandler} 的信封形态保持一致 —— 前端 {@code http.ts} 按 body.code 分支，无需改动。</p>
 *
 * <p><b>顺序</b>：紧随 {@link TraceIdGatewayFilter} 之后（HIGHEST + 10），保证拒绝响应也带 traceId 便于排查。</p>
 */
@Slf4j
@Component
public class AuthGatewayFilter implements GlobalFilter, Ordered {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String USER_TOKEN_HEADER = "X-User-Token";

    /**
     * 用户态受保护路径：与服务侧 {@code ApiAccessWebConfig} 的 requireUser / requireSessionUser 列表逐一对应
     * （{@code /api/seckill/users/me/orders} 由 {@code [^/]+} 覆盖）。
     */
    private static final List<Pattern> USER_PROTECTED_PATHS = List.of(
            Pattern.compile("^/api/seckill/execute$"),
            Pattern.compile("^/api/seckill/activities/[^/]+/check$"),
            Pattern.compile("^/api/seckill/users/[^/]+/orders$"),
            Pattern.compile("^/api/seckill/orders/[^/]+$"),
            Pattern.compile("^/api/auth/me$"),
            Pattern.compile("^/api/auth/csrf$"),
            Pattern.compile("^/api/auth/logout$")
    );

    private final String adminToken;
    private final String sessionCookieName;

    public AuthGatewayFilter(@Value("${security.admin-token:}") String adminToken,
                             @Value("${gateway.auth.session-cookie-name:FLASH_SESSION}") String sessionCookieName) {
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.sessionCookieName = sessionCookieName == null ? "FLASH_SESSION" : sessionCookieName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getRawPath();

        // CORS 预检不带业务凭证，必须放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        // ① 管理端：全量校验管理令牌（网关层拒绝，不再转发到下游）
        if (isAdminPath(path)) {
            if (adminToken.isEmpty()) {
                return reject(exchange, "管理端访问令牌未配置");
            }
            if (!adminToken.equals(request.getHeaders().getFirst(ADMIN_TOKEN_HEADER))) {
                return reject(exchange, "管理端访问令牌无效");
            }
            return chain.filter(exchange);
        }

        // ② 用户态：仅校验凭证存在性（有效性 / CSRF / 归属由服务侧细校验）
        if (isUserProtected(path) && !hasCredential(request)) {
            return reject(exchange, "请先登录");
        }

        return chain.filter(exchange);
    }

    private boolean isAdminPath(String path) {
        return "/api/admin".equals(path) || path.startsWith("/api/admin/");
    }

    private boolean isUserProtected(String path) {
        return USER_PROTECTED_PATHS.stream().anyMatch(p -> p.matcher(path).matches());
    }

    private boolean hasCredential(ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(sessionCookieName);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return true;
        }
        String token = request.getHeaders().getFirst(USER_TOKEN_HEADER);
        return token != null && !token.isBlank();
    }

    /**
     * 直接返回业务错误信封（HTTP 200 + code）。
     * 手写 JSON 而不引 ObjectMapper：消息文本均为本类常量，无转义风险，也避免给响应式网关引入额外序列化依赖。
     */
    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setStatusCode(HttpStatus.OK);

        String body = "{\"code\":" + ResultCode.UNAUTHORIZED.getCode()
                + ",\"message\":\"" + message + "\""
                + ",\"requestId\":\"" + RequestIdUtil.generate() + "\""
                + ",\"timestamp\":" + System.currentTimeMillis() + "}";

        log.warn("gw 准入拒绝 {} {} -> {} {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getRawPath(), ResultCode.UNAUTHORIZED.getCode(), message);

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
