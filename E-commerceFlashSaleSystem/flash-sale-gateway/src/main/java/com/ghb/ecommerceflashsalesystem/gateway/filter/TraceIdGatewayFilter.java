package com.ghb.ecommerceflashsalesystem.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * traceId 注入过滤器（P1-1）——链路三处贯通的第一站。
 *
 * <p>入口确保 {@code X-Trace-Id} 存在（缺失或非法则生成），透传给下游单体并回填响应头；
 * 下游 {@code TraceIdFilter} 会把它写入 MDC（日志）并在 execute 时随 Stream 消息传递，
 * 消费端再从消息体恢复 MDC，形成 HTTP → 日志 → 消息 → 消费侧日志的同链路视图。</p>
 */
@Slf4j
@Component
public class TraceIdGatewayFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 只接受安全字符集，避免把任意串带进日志/响应头（防日志注入） */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9\\-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || !SAFE_TRACE_ID.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String finalTraceId = traceId;

        // 响应头回填：提交前 set（覆盖）而非提前 set —— 下游单体也会回填同名头，
        // 提前 set 会与下游头合并成重复值（实测出现两条 X-Trace-Id）。
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, finalTraceId);
            return Mono.empty();
        });

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        long start = System.currentTimeMillis();

        // WebFlux 无 Servlet 的 MDC 线程模型：日志显式携带 traceId 即可（Reactor 上下文由 P1-2 的 OTel 接管）
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> log.info("gw traceId={} {} {} -> {} ({} ms)",
                        finalTraceId, method, path, signal, System.currentTimeMillis() - start));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
