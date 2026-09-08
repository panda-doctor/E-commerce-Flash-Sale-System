package com.ghb.ecommerceflashsalesystem.common.util;

/**
 * 分布式 ID 生成器（单机版 · JS 安全整数适配）
 *
 * 设计说明：
 * - 输出恒小于 2^53（≈9.007e15 = JS Number.MAX_SAFE_INTEGER），Long ID 经 JSON 直传
 *   前端不丢精度，解决旧版 timestamp*1_000_000（≈1.78e18）导致的活动/订单/榜单
 *   全链路失真的问题（新建活动后详情/榜单/指标全部 404）。
 * - ID = ((nowMs - EPOCH_MS) &lt;&lt; 10) | sequence(0~1023)，单调递增、单机内全局唯一。
 * - EPOCH 固定为 2024-01-01 00:00:00 UTC：当前（2026-09）差值约 8.4e10ms，左移 10 位后
 *   约 8.6e13，距 2^53 上限仍有约 3 个数量级余量，可稳定支撑多年演示。
 * - 单毫秒最多 1024 个 ID（每秒约百万），满足教学并发演示；超出时自旋等待下一毫秒。
 * 后续若需多机扩容，建议将低位拆分为「分片位 + 序列位」或接入 Redis 号段，避免回拨碰撞。
 */
public class IdGenerator {
    /** 起始纪元：2024-01-01 00:00:00 UTC（毫秒） */
    private static final long EPOCH_MS = 1704067200000L;
    /** 序列掩码：0~1023 */
    private static final long SEQUENCE_MASK = 0x3FFL;

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成下一个唯一 ID（恒小于 2^53，前端可安全使用 Number 表示）
     */
    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            // 时钟回拨：沿用上一毫秒，避免 ID 倒退引发唯一键冲突
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                // 本毫秒序列耗尽，自旋等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH_MS) << 10) | sequence;
    }

    private static long waitNextMillis(long currentMillis) {
        long next = System.currentTimeMillis();
        while (next <= currentMillis) {
            next = System.currentTimeMillis();
        }
        return next;
    }
}
