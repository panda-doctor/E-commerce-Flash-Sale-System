package com.ghb.ecommerceflashsalesystem.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成器，线程安全，格式：SK + yyyyMMddHHmmssSSS + 4位序号
 * 例如：SK202609041530451234
 *
 * 【5000 并发压测复盘】曾用 System.currentTimeMillis() 做"是否同毫秒/序号归零"的判断，
 * 却用 LocalDateTime.now() 拼输出文本 —— 两个时钟源在毫秒跨边界瞬间可能错位：
 * 边界请求的序号在新的毫秒被归零，而文本仍输出旧毫秒/新毫秒，导致"同文本同序号"
 * 的订单号被重复生成。后果：Lua 扣减与 XADD 都成功，但 DB uk_order_no 唯一键把第二个
 * 成功单当作幂等合并，出现"库存归 0 而订单少 1"的隐蔽丢单。
 *
 * 修复：序号判定与输出文本取自【同一个时钟源】——把 now 毫秒统一换算为 LocalDateTime，
 * 消除双时钟错位。
 */
public class OrderNoGenerator {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static long LAST_TIMESTAMP = -1;
    private static int SEQUENCE = 0;
    /** 同毫秒最大序号（超限则让出当前毫秒，保证永不重复） */
    private static final int MAX_SEQUENCE = 9999;

    private OrderNoGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static synchronized String generate() {
        long now = System.currentTimeMillis();
        if (now == LAST_TIMESTAMP) {
            SEQUENCE++;
            if (SEQUENCE > MAX_SEQUENCE) {
                // 极端情况：单毫秒并发超 1 万，让出到下一毫秒再继续
                do {
                    now = System.currentTimeMillis();
                } while (now == LAST_TIMESTAMP);
                LAST_TIMESTAMP = now;
                SEQUENCE = 0;
            }
        } else {
            SEQUENCE = 0;
            LAST_TIMESTAMP = now;
        }
        String timePart = FORMATTER.format(Instant.ofEpochMilli(now).atZone(ZONE));
        return "SK" + timePart + String.format("%04d", SEQUENCE);
    }
}
