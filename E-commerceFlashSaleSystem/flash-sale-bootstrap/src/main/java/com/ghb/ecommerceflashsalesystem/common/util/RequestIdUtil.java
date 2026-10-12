package com.ghb.ecommerceflashsalesystem.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求追踪编号生成工具
 * <p>
 * 生成格式：时间戳（14位，yyyyMMddHHmmss）+ 4位自增序列号（0001~9999循环）
 * <p>
 * 线程安全：使用 {@link AtomicLong} 保证序列号原子递增，{@link DateTimeFormatter} 为线程安全实现。
 */
public final class RequestIdUtil {
        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        private static final AtomicLong SEQUENCE = new AtomicLong(0);

        private RequestIdUtil() {
            throw new IllegalStateException("Utility class");
        }

    /**
     * 生成请求追踪编号
     *
     * @return 唯一编号，例如 "202607271200001001"
     */
    public static String generate(){
        // 1. 获取当前时间戳（线程安全）
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        // 2. 原子自增并取模，得到 0~9999 的循环序列
        long seq = SEQUENCE.incrementAndGet() % 10000;
        // 首次 increment 得 1 → 1%10000=1，格式化为 "0001"
        // 当增至 10000 时，10000%10000=0 → "0000"，下一次又回到 1

        // 3. 拼接
        return timestamp + String.format("%04d", seq);
    }
}
