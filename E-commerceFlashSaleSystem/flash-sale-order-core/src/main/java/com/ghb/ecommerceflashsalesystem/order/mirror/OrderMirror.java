package com.ghb.ecommerceflashsalesystem.order.mirror;

import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillMessageLog;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.infra.db.DualWriteMirror;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * order 域的「权威 → 副本」镜像写入器（P4 Day 4，过渡期组件，旧库表下线后本类一并删除）。
 *
 * <p>方向与 product / activity 一致：<b>权威 = {@code flash_sale_order}</b>（order 应用读写），
 * <b>副本 = 旧库 {@code flash_sale}</b>（单体侧 analytics 的 {@code OrderQueryAdapter} 读它，
 * 给榜单/指标补订单号）。</p>
 *
 * <p><b>三条纪律</b>：</p>
 * <ol>
 *   <li><b>必须在权威库写入成功之后调用</b>（含 insert 之后 —— 那时自增 id 才回填到实体上）；</li>
 *   <li><b>SQL 覆盖全部业务列</b>：{@code updateById} 的镜像也走同一条 upsert，避免漏列导致副本丢字段；</li>
 *   <li><b>时间戳用 {@code IFNULL(?, NOW())}</b>：MyBatis-Plus 默认跳过 null 字段 → 实体里的
 *       {@code createdAt} 常为 null（由 DB 默认值填），直接绑 null 会写坏副本。</li>
 * </ol>
 *
 * <p>镜像默认关闭（{@code DualWriteProperties.enabled} 类内 false）→ 单体、集成测试零行为变化；
 * 只有 {@code flash-sale-order} 应用的 yaml 显式开启。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderMirror {

    /** 订单表 upsert（列顺序与 {@code seckill_order} 一致；副本表名同名，连接串已指向旧库） */
    private static final String UPSERT_ORDER = """
            INSERT INTO seckill_order (id, order_no, activity_id, product_id, user_id, seckill_price,
                                       status, stream_message_id, failure_reason, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, IFNULL(?, NOW()), IFNULL(?, NOW()))
            ON DUPLICATE KEY UPDATE order_no = VALUES(order_no), activity_id = VALUES(activity_id),
                                    product_id = VALUES(product_id), user_id = VALUES(user_id),
                                    seckill_price = VALUES(seckill_price), status = VALUES(status),
                                    stream_message_id = VALUES(stream_message_id),
                                    failure_reason = VALUES(failure_reason),
                                    updated_at = VALUES(updated_at)
            """;

    /** 消息日志 upsert（PENDING / SUCCESS / FAILED / DEAD 四种状态共用） */
    private static final String UPSERT_MESSAGE_LOG = """
            INSERT INTO seckill_message_log (id, stream_key, stream_message_id, consumer_group, consumer_name,
                                             order_no, activity_id, product_id, user_id, retry_count, status,
                                             error_message, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, IFNULL(?, NOW()), IFNULL(?, NOW()))
            ON DUPLICATE KEY UPDATE retry_count = VALUES(retry_count), status = VALUES(status),
                                    error_message = VALUES(error_message), updated_at = VALUES(updated_at)
            """;

    private final DualWriteMirror mirror;

    /** 订单落库 / 状态变更后镜像（insert 之后 id 已回填，可安全带上主键） */
    public void upsertOrder(SeckillOrder order) {
        if (order == null) {
            return;
        }
        mirror.mirrorOrder(UPSERT_ORDER,
                order.getId(), order.getOrderNo(), order.getActivityId(), order.getProductId(),
                order.getUserId(), order.getSeckillPrice(), order.getStatus(), order.getStreamMessageId(),
                order.getFailureReason(), order.getCreatedAt(), order.getUpdatedAt());
    }

    /** 消息日志镜像（建单/失败/死信/回放四处更新点都调它） */
    public void upsertMessageLog(SeckillMessageLog logEntity) {
        if (logEntity == null) {
            return;
        }
        mirror.mirrorOrder(UPSERT_MESSAGE_LOG,
                logEntity.getId(), logEntity.getStreamKey(), logEntity.getStreamMessageId(),
                logEntity.getConsumerGroup(), logEntity.getConsumerName(), logEntity.getOrderNo(),
                logEntity.getActivityId(), logEntity.getProductId(), logEntity.getUserId(),
                logEntity.getRetryCount(), logEntity.getStatus(), logEntity.getErrorMessage(),
                logEntity.getCreatedAt(), logEntity.getUpdatedAt());
    }
}
