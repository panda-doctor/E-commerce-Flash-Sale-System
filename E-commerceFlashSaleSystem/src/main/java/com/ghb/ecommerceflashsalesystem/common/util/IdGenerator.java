package com.ghb.ecommerceflashsalesystem.common.util;

/**
 * 分布式 ID 生成器（单机版，基于时间戳 + 序号）
 * 每毫秒最多生成 1,000,000 个 ID，确保全局唯一且递增。
 */
public class IdGenerator {
    private static long lastTimestamp = -1;
    private static long sequence = 0;
    private static final long SEQUENCE_BASE = 1_000_000L;

    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成下一个唯一 ID
     */
    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp == lastTimestamp) {
            sequence ++;
        }else{
            sequence = 0;
            lastTimestamp = timestamp;
        }
        // 每毫秒最多 100 万个 ID，足够应对高并发
        return timestamp * SEQUENCE_BASE + sequence;
    }
}
