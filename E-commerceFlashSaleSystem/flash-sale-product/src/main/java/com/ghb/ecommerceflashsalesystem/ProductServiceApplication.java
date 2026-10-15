package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商品服务启动类（P3 Day 1：product 域服务化骨架）。
 *
 * <p>端口 8084、注册名 flash-sale-product。启动类必须位于根包
 * {@code com.ghb.ecommerceflashsalesystem}，组件扫描才能覆盖共享库 Bean
 * （infra 的 Redis/MyBatis-Plus 配置、GlobalExceptionHandler 等）。</p>
 *
 * <p>Day 1 只启动服务、不改路由：单体仍持有商品接口（controllers 仍在
 * flash-sale-product-core 中），因此行为零变化。</p>
 */
@MapperScan("com.ghb.ecommerceflashsalesystem.product.mapper")
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
