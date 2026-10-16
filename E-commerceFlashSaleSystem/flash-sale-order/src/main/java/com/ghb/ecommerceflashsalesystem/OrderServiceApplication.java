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
 * <p>数据源 = **权威库 {@code flash_sale_order}**（P4 Day 4 拆分），写后反向镜像到旧库副本。
 * P4 Day 7 会话校验跨服务化后，本应用不再读写 auth 的两张表。</p>
 *
 * <p><b>为什么收窄 {@code scanBasePackages}（P4 Day 7）</b>：本应用为了拿会话校验的 HTTP 适配器
 * 引入 {@code flash-sale-remote}，而 remote 依赖 {@code flash-sale-activity-core}（为了端口接口）
 * —— 默认扫描会把 activity-core 的 {@code @Service}（如 {@code SeckillCacheService}、
 * {@code SeckillActivityServiceImpl}）一并实例化，它们要 {@code activity.mapper}（本应用没有）
 * → **启动即失败**。这是"域库的 Bean 会被任何扫描它的运行时实例化"的第三次现身
 * （Day 5 是 ai 的 {@code SeckillCacheService}，Day 7 是单体与 order）—— 解法同：**收窄扫描面**。</p>
 */
@MapperScan(basePackages = {"com.ghb.ecommerceflashsalesystem.order.mapper",
        "com.ghb.ecommerceflashsalesystem.analytics.mapper"})
// P4 Day 7（会话校验跨服务化）：本应用**不再需要 auth 的 mapper**，user.mapper 已从扫描面移除，
// 双数据源过渡桥（order.config.DataSourceConfig + module.auth-db）随之删除 ——
// 回到"单数据源 + 单 SqlSessionFactory（自动配置）"，Day 4 的 sqlSessionFactoryRef 也不再需要。
// 会话校验现在由 flash-sale-auth 的内部接口承担（remote 适配器 + SessionGuard）。
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.ghb.ecommerceflashsalesystem.order",
        "com.ghb.ecommerceflashsalesystem.analytics",
        "com.ghb.ecommerceflashsalesystem.user",
        "com.ghb.ecommerceflashsalesystem.infra",
        "com.ghb.ecommerceflashsalesystem.remote"
})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
