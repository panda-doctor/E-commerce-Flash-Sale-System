package com.ghb.ecommerceflashsalesystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * AI 客服服务启动类（P2 Day 3：ai 域服务化）。
 *
 * <p>端口 8083、注册名 {@code flash-sale-ai}；网关把 {@code /api/support/**} 导流到本服务。</p>
 *
 * <p><b>为什么启动类必须放在根包</b>：{@code @SpringBootApplication} 隐含
 * {@code @ComponentScan(本类所在包)}。放在根包 {@code com.ghb.ecommerceflashsalesystem} 下，
 * 才能扫到共享库里的 Bean（如 infra 的 Redis / MyBatis-Plus 配置、GlobalExceptionHandler）
 * 与各域组件；若放在 {@code ...ai} 子包，infra 配置不会被加载，启动即失败。</p>
 *
 * <p><b>依赖边界（P4 Day 5 起更硬）</b>：本服务只依赖 common / domain-model / infra /
 * flash-sale-remote（跨进程适配器）—— **不再依赖 product / activity 的库**，也不再持有任何 mapper。
 * 目录（在售商品 / 秒杀活动）改为调两个服务的**公开读接口**（见 {@code ai.port.CatalogQueryPort}），
 * 所以本服务**没有数据源**：这是"ai 无库"（P2 Day 3 定下的目标）真正落地。</p>
 *
 * <p>因为不引入 seckill / order，也不会带入 Redis Stream 消费者（不会误建单）。</p>
 *
 * <p><b>⚠️ 为什么要收窄 scanBasePackages</b>：默认扫描是以本类包为根的
 * {@code com.ghb.ecommerceflashsalesystem}，会把 classpath 上**所有域库**的
 * {@code @Service/@Component} 一并扫进来 —— 而 {@code flash-sale-remote} 依赖
 * {@code flash-sale-activity-core}（为了端口的接口），于是 activity-core 的
 * {@code SeckillCacheService} 也会被实例化，它要 {@code SeckillActivityMapper}
 * （ai 已无 mapper/数据源）→ **启动即失败**（实测：{@code Parameter 1 of constructor in
 * SeckillCacheService required a bean of type SeckillActivityMapper}）。
 * 收窄后 ai 只扫自己 + infra（Redis/镜像等基础设施）+ remote（跨进程客户端）+ common/contracts，
 * 与"ai 不依赖任何域库"的边界一致。</p>
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class,
        scanBasePackages = {
                "com.ghb.ecommerceflashsalesystem.ai",
                "com.ghb.ecommerceflashsalesystem.infra",
                "com.ghb.ecommerceflashsalesystem.remote",
                "com.ghb.ecommerceflashsalesystem.common",
                "com.ghb.ecommerceflashsalesystem.contracts"
        })
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
