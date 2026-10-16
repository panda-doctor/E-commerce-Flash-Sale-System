package com.ghb.ecommerceflashsalesystem;

import com.ghb.ecommerceflashsalesystem.seckill.service.SeckillStockService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
/**
 * P4 Day 10 · Step A：**收窄装配面，彻底摘掉旧库依赖**。
 *
 * <p>此前 `@MapperScan` 扫 product/activity/order/analytics 四个包，于是一批**永远不会被调用**
 * 的域 Bean（`SeckillActivityServiceImpl` / `SeckillCacheService` / `LocalProductCatalogAdapter`）
 * 被实例化 —— 它们只是为了让旧库的 mapper 有宿主。现在：</p>
 * <ul>
 *   <li>{@code scanBasePackages} 只留本应用真正需要的：自己的 {@code .controller} + {@code .analytics}、
 *       auth 的 {@code .user}（会话守卫）、{@code .infra}、{@code .remote}（HTTP 适配器）、
 *       {@code common}/{@code contracts}；</li>
 *   <li>**注解 `@MapperScan` 整段删除** —— 生产已无任何 mapper 需求：快照写走
 *       {@code ObjectProvider}（order 应用才是属主）、订单读走 HTTP、会话校验走 HTTP；</li>
 *   <li>唯一仍需的域 Bean 是 {@link SeckillStockService}（`/internal/seckill` 库存操作，
 *       字段只有 RedisTemplate、不碰 mapper）→ 用 {@code @Import} **精确引入**，
 *       而不是把整个 {@code seckill.service} 包扫回来（同包的 {@code service.impl.SeckillServiceImpl}
 *       要 activity.mapper，会被一起带进来）。</li>
 * </ul>
 * <p>测试侧的需求（约十个集成测试有意把单体当"全域装配沙盘"）由测试树的
 * {@code analytics.TestDomainContextConfig} 显式补齐 —— 装配面与测试沙盘职责分离。</p>
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.ghb.ecommerceflashsalesystem.controller",
        "com.ghb.ecommerceflashsalesystem.analytics",
        "com.ghb.ecommerceflashsalesystem.user",
        "com.ghb.ecommerceflashsalesystem.infra",
        "com.ghb.ecommerceflashsalesystem.remote",
        "com.ghb.ecommerceflashsalesystem.common",
        "com.ghb.ecommerceflashsalesystem.contracts"
})
// ⚠️ P4 Day 10 · Step C：本想在这里 exclude = DataSourceAutoConfiguration.class（单体已无 mapper，
// 数据源无人使用）—— 实测**启动失败**：`HealthController` 的构造器直接依赖 `javax.sql.DataSource`
// （健康检查里的 DB 探针）。所以去数据源必须与"健康检查改为可选依赖（ObjectProvider<DataSource>）"
// 一起做，详见 plan.md 的 Step C 记录。此处保持可启动状态，不半程改动。
@Import(SeckillStockService.class)
public class ECommerceFlashSaleSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceFlashSaleSystemApplication.class, args);
    }

}
