package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * <p><b>依赖边界</b>：本服务只依赖 common / domain-model / infra / product / activity，
 * 不含 seckill / order —— 因此不会带入 Redis Stream 消费者（不会误建单）。
 * Mapper 需显式声明两个域包（ai 域的目录查询要用到商品与活动 Mapper）。</p>
 */
@MapperScan({
        "com.ghb.ecommerceflashsalesystem.product.mapper",
        "com.ghb.ecommerceflashsalesystem.activity.mapper"
})
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
