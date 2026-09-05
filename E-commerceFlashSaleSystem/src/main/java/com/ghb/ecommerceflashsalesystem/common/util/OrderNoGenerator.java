package com.ghb.ecommerceflashsalesystem.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成器，线程安全，格式：SK + yyyyMMddHHmmssSSS + 4位序号
 * 例如：SK202609041530451234
 */
public class OrderNoGenerator {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static long LASTTIMESTAMP = -1;
    private static int SEQUENCE = 0;

    private OrderNoGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static synchronized String generate() {
        long now = System.currentTimeMillis();  //获取当前系统时间的毫秒级时间戳
        if (now == LASTTIMESTAMP){
            SEQUENCE++;
        }else{
            SEQUENCE = 0;
            LASTTIMESTAMP = now;
        }
        // 每毫秒最多 9999 个订单号，足够
        String timePart = FORMATTER.format(LocalDateTime.now());
        return "SK" + timePart + String.format("%04d", SEQUENCE % 10000);
    }

}
