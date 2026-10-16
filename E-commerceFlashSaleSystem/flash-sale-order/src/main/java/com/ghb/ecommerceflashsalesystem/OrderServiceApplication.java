package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单域应用（P4 Day 3，端口 8087）。
 *
 * <p>承载：三个订单查询入口（{@code GET /api/seckill/orders/{orderNo}}、
 * {@code /api/seckill/users/{userId}/orders}、{@code /api/seckill/users/me/orders}）、
 * 死信回放（{@code POST /api/admin/seckill/dead-letters/replay}），以及
 * <b>Stream 消费调度</b>（{@code order.scheduler.StreamConsumerScheduler}，150ms 新消息 + 5s PEL 重试）。</p>
 *
 * <p><b>为什么它带 {@code @EnableScheduling}</b>：全仓唯一的定时任务就在本域（消费调度）。
 * 消费者本体 {@code SeckillOrderConsumer} 留在 {@code flash-sale-order-core}（4 个集成测试注入它、
 * 由测试自行驱动消费），**只有调度入口随域迁到应用** —— 单体因此不再自动消费
 * （类不在其 classpath 上，且其 yaml 的 {@code flash.stream.auto-poll} 已置 false，双保险）。
 * <b>重复消费 = 重复建单</b>，这是本日唯一的高危点。</p>
 *
 * <p><b>三个 mapper 包</b>：{@code order.mapper}（`seckill_order` / `seckill_message_log`）、
 * {@code analytics.mapper}（`seckill_activity_snapshot`，analytics 的 `SeckillRankService` 与
 * `MetricsServiceImpl` 在消费链路上被用到）、{@code user.mapper}（auth-core 的用户表）。</p>
 *
 * <p>数据源暂指**旧库 flash_sale**：order 的三张表尚未拆独立 schema，且 auth 的用户表也只在那里
 * —— order 的「库」拆分是后续独立的一天。</p>
 */
@MapperScan({"com.ghb.ecommerceflashsalesystem.order.mapper",
        "com.ghb.ecommerceflashsalesystem.analytics.mapper",
        "com.ghb.ecommerceflashsalesystem.user.mapper"})
@EnableScheduling
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
