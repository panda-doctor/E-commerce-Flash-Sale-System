package com.ghb.ecommerceflashsalesystem.stream.producer;

import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.dto.message.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderStreamProducer {
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 发送订单消息到 Stream
     *
     * @param seckillOrderMessage 订单消息
     * @return 消息 ID（格式：时间戳-序号）
     */
    public RecordId sendMessage(SeckillOrderMessage seckillOrderMessage) {
        ObjectRecord<String, SeckillOrderMessage> messageObjectRecord =
                StreamRecords.objectBacked(seckillOrderMessage)
                .withStreamKey(CacheKeyConstant.SECKILL_ORDER_STREAM);

        RecordId recordId = redisTemplate.opsForStream().add(messageObjectRecord);
        log.info("订单消息已发送到 Stream, orderNo={}, recordId={}", seckillOrderMessage.getOrderNo(), recordId);
        return recordId;
    }
}
