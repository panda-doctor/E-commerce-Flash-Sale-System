package com.ghb.ecommerceflashsalesystem.common.constant;

/**
 * 链路追踪常量（P1 基础设施）。
 *
 * <p>三处贯通约定：HTTP 头 {@link #HEADER_NAME} → MDC {@link #MDC_KEY}（日志 pattern 的 %X{traceId}）
 * → Stream 消息体字段 {@link #MESSAGE_FIELD} → 消费端恢复 MDC。
 * 网关、单体过滤器、消息生产者与消费者共用同一组常量，避免各写各的字面量。</p>
 */
public final class TraceConstant {

    private TraceConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** MDC 键名：logging.pattern 中使用 %X{traceId} */
    public static final String MDC_KEY = "traceId";

    /** HTTP 头名（网关 → 单体 → 响应回填） */
    public static final String HEADER_NAME = "X-Trace-Id";

    /** Stream 消息体字段名（明文 Map 投递，消费端据此续接链路） */
    public static final String MESSAGE_FIELD = "traceId";
}
