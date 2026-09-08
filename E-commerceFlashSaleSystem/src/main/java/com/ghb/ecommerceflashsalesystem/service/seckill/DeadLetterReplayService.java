package com.ghb.ecommerceflashsalesystem.service.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.message.SeckillOrderMessage;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillMessageLog;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.MessageLogStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillMessageLogMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.stream.producer.SeckillOrderStreamProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 死信人工回放。
 *
 * <p>死信进入时（订单未落库场景）已经补偿库存和令牌，因此不能把旧消息直接塞回主 Stream；
 * 必须先重新执行原子占位，确保回放消息再次拥有库存凭证，避免重复建单或库存为负。
 *
 * <p>回放数据源是【死信流 Redis 快照】而非 DB 日志：seckill_message_log 表不含成交价/请求时间
 * 列（仅下单所需业务列），价格快照在消费失败瞬间已随死信体（明文 Map）写入
 * seckill:order:dead:stream，人工回放需按 originalMessageId 找回该死信体重建消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterReplayService {

    private final SeckillMessageLogMapper messageLogMapper;
    private final SeckillOrderMapper orderMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> seckillExecuteScript;
    private final SeckillOrderStreamProducer streamProducer;

    /**
     * 人工回放一条死信：从死信流找回原消息快照 → 校验 DEAD 状态与订单幂等 →
     * 重新原子占位（扣库存 + 建令牌）→ 重投主 Stream → 成功后 XDEL 死信条目。
     *
     * @param originalMessageId 原主 Stream 消息 ID（消费者转死信时写入死信体 originalMessageId 字段）
     * @return 回放后新消息的 Stream messageId
     */
    public String replay(String originalMessageId) {
        // 1. 日志校验：只有 DEAD 状态的消息才允许人工回放
        SeckillMessageLog deadLog = messageLogMapper.selectOne(new LambdaQueryWrapper<SeckillMessageLog>()
                .eq(SeckillMessageLog::getStreamMessageId, originalMessageId));
        if (deadLog == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "死信消息不存在");
        }
        if (deadLog.getStatus() == null || deadLog.getStatus() != MessageLogStatusEnum.DEAD.getCode()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该消息不是可回放的死信状态");
        }

        // 2. 死信流定位原消息快照（权威数据源：含成交价 / 请求时间等完整字段）
        MapRecord<String, Object, Object> deadRecord = findDeadRecord(originalMessageId);
        if (deadRecord == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,
                    "死信流中不存在该消息（可能已被回放清理），originalMessageId=" + originalMessageId);
        }
        Map<Object, Object> body = deadRecord.getValue();
        Long activityId = toLong(body.get("activityId"));
        Long productId = toLong(body.get("productId"));
        Long userId = toLong(body.get("userId"));
        String orderNo = toStr(body.get("orderNo"));
        Long seckillPrice = toLong(body.get("seckillPrice"));
        Long requestTime = toLong(body.get("requestTime"));
        if (activityId == null || productId == null || userId == null || orderNo == null
                || seckillPrice == null || requestTime == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "死信快照缺少订单必要字段，无法安全回放");
        }

        // 3. 幂等校验：订单已落库则无需回放（落库成功但入榜失败进死信的极端场景不补偿，也不需要重投）
        SeckillOrder existingOrder = orderMapper.selectByOrderNo(orderNo);
        if (existingOrder != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订单已落库，无需回放");
        }

        // 4. 重新原子占位：库存 -1 + 令牌 SET，与首次入队完全一致
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + activityId;
        String tokenKey = CacheKeyConstant.SECKILL_USER_PREFIX + activityId + ":" + userId;
        Long reserveResult = redisTemplate.execute(seckillExecuteScript, Arrays.asList(stockKey, tokenKey), "1",
                CacheKeyConstant.SECKILL_USER_TOKEN_TTL);
        if (reserveResult == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "回放时无法预占库存，请稍后重试");
        }
        if (reserveResult == -1) {
            throw new BusinessException(ResultCode.OUT_OF_STOCK, "回放失败：库存不足");
        }
        if (reserveResult == -2) {
            throw new BusinessException(ResultCode.DUPLICATE_PURCHASE, "回放失败：用户已有进行中的订单");
        }

        // 5. 重建消息并重投主 Stream
        SeckillOrderMessage replayMessage = SeckillOrderMessage.builder()
                .activityId(activityId)
                .productId(productId)
                .userId(userId)
                .orderNo(orderNo)
                .seckillPrice(seckillPrice)
                .requestTime(requestTime)
                .build();
        try {
            RecordId recordId = streamProducer.sendMessage(replayMessage);
            // 回放成功后清理死信条目，防止同一条死信被重复回放
            redisTemplate.opsForStream().delete(CacheKeyConstant.SECKILL_DEAD_STREAM,
                    deadRecord.getId().getValue());
            deadLog.setErrorMessage("已人工回放，新消息ID=" + recordId.getValue());
            messageLogMapper.updateById(deadLog);
            log.warn("死信已人工回放，originalMessageId={}, replayMessageId={}", originalMessageId,
                    recordId.getValue());
            return recordId.getValue();
        } catch (Exception e) {
            // 与首次入队一致：消息未写入时立即归还本次回放占用的资源
            redisTemplate.opsForValue().increment(stockKey, 1);
            redisTemplate.delete(tokenKey);
            log.error("死信回放写入主 Stream 失败，已回补库存和令牌，originalMessageId={}", originalMessageId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "死信回放失败，请稍后重试");
        }
    }

    /** 遍历死信流，按 originalMessageId 找回该死信对应的快照记录（死信量小，教学实现直接遍历）。 */
    private MapRecord<String, Object, Object> findDeadRecord(String originalMessageId) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(CacheKeyConstant.SECKILL_DEAD_STREAM, Range.unbounded());
        if (records == null || records.isEmpty()) {
            return null;
        }
        for (MapRecord<String, Object, Object> record : records) {
            Map<Object, Object> body = record.getValue();
            if (originalMessageId.equals(toStr(body == null ? null : body.get("originalMessageId")))) {
                return record;
            }
        }
        return null;
    }

    /** Map 值安全转 Long：兼容 String / Number / null */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Map 值安全转 String：null 兜底 */
    private String toStr(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }
}
