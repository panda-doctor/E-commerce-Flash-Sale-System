package com.ghb.ecommerceflashsalesystem.stream.producer;

import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.dto.message.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderStreamProducer {
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 发送订单消息到 Stream
     *
     * 【复盘】v1 用 StreamRecords.objectBacked(bean) 投递：ObjectRecord 会把 bean 字段经序列化器编码后再写入，
     * 消费者 XREADGROUP 读回 MapRecord 时字段值变成 Base64 字符串（如 activityId=200 读成 "MjAw"），
     * 解析直接抛 NumberFormatException。修正：改用【明文 Map】投递（字段值均为可读字符串），
     * 消费者侧用 toLong/toStr 安全转换即可。
     *
     * @param seckillOrderMessage 订单消息
     * @return 消息 ID（格式：时间戳-序号）
     */
    public RecordId sendMessage(SeckillOrderMessage seckillOrderMessage) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("activityId", String.valueOf(seckillOrderMessage.getActivityId()));
        body.put("productId", String.valueOf(seckillOrderMessage.getProductId()));
        body.put("userId", String.valueOf(seckillOrderMessage.getUserId()));
        body.put("orderNo", seckillOrderMessage.getOrderNo());
        body.put("seckillPrice", String.valueOf(seckillOrderMessage.getSeckillPrice()));
        body.put("requestTime", String.valueOf(seckillOrderMessage.getRequestTime()));

        RecordId recordId = redisTemplate.opsForStream().add(
                StreamRecords.mapBacked(body).withStreamKey(CacheKeyConstant.SECKILL_ORDER_STREAM));

        log.info("订单消息已发送到 Stream, orderNo={}, recordId={}", seckillOrderMessage.getOrderNo(), recordId);
        return recordId;
    }
}
