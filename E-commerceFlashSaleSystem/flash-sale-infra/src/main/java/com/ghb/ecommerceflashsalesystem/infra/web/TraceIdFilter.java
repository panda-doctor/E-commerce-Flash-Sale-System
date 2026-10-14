package com.ghb.ecommerceflashsalesystem.infra.web;

import com.ghb.ecommerceflashsalesystem.common.constant.TraceConstant;
import com.ghb.ecommerceflashsalesystem.common.util.RequestIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * traceId 过滤器（P1 基础设施）——链路三处贯通的第二站。
 *
 * <p>入口解析 {@code X-Trace-Id}（网关注入；缺失/非法则本地生成）→ 写入 MDC（日志）与响应头；
 * 秒杀 execute 侧由 {@code SeckillOrderStreamProducer} 把同一 traceId 写入 Stream 消息，
 * 消费端 {@code SeckillOrderConsumer} 再恢复 MDC，实现跨线程 / 跨消息的同链路日志。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 与网关侧保持一致的安全字符集校验，避免外部任意串进入日志（日志注入） */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9\\-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceConstant.HEADER_NAME);
        if (traceId == null || !SAFE_TRACE_ID.matcher(traceId).matches()) {
            traceId = RequestIdUtil.generate();
        }
        MDC.put(TraceConstant.MDC_KEY, traceId);
        response.setHeader(TraceConstant.HEADER_NAME, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceConstant.MDC_KEY);
        }
    }
}
