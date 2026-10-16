package com.ghb.ecommerceflashsalesystem.analytics;

import com.ghb.ecommerceflashsalesystem.adapter.LocalProductCatalogAdapter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * P4 Day 10 · Step A 的**测试侧装配**：把"测试沙盘"与"生产装配面"分开表达。
 *
 * <p>背景：单体生产侧本轮收窄了 {@code scanBasePackages} 并删掉了 {@code @MapperScan}（它不再需要
 * 任何域 mapper）。但 bootstrap 的集成测试**有意**把单体当成全域装配沙盘 ——
 * `CreateBusinessValidationTest` 注入 `ProductServiceImpl`/`SeckillActivityServiceImpl`、
 * `AdminSeckillResetStockTest` 注入 `SeckillCacheService`、`SeckillMessageReliabilityTest` 注入
 * order-core 的消费者、`SeckillMetricsTest` 需要 `analytics.mapper`（快照断言）……
 * 这些需求属于**测试**，不该让生产扫描面替它背着。</p>
 *
 * <p><b>为什么放在这个包</b>：本类必须落在**被扫描的包**里才会被 {@code @SpringBootTest} 拾取 ——
 * 生产的扫描面里正好包含 {@code …analytics}（rank/metrics 所在），而本类只存在于**测试树**，
 * 因此生产上下文不受影响 ✓。</p>
 */
@Configuration
@ComponentScan({
        "com.ghb.ecommerceflashsalesystem.product",
        "com.ghb.ecommerceflashsalesystem.activity",
        "com.ghb.ecommerceflashsalesystem.order",
        "com.ghb.ecommerceflashsalesystem.seckill"
})
@MapperScan({
        "com.ghb.ecommerceflashsalesystem.product.mapper",
        "com.ghb.ecommerceflashsalesystem.activity.mapper",
        "com.ghb.ecommerceflashsalesystem.order.mapper",
        "com.ghb.ecommerceflashsalesystem.analytics.mapper"
})
@Import(LocalProductCatalogAdapter.class)
public class TestDomainContextConfig {
}
