package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@MapperScan({
        // P4 Day 7：user.mapper **已从扫描面移除** —— 会话校验跨服务化后（SessionGuard ->
        // SessionValidationPort，默认走 auth 的内部接口），单体不再需要 auth 的两张表。
        // auth 的写侧服务（UserAccountService / UserSessionService）带 @ConditionalOnProperty
        // (module.auth.mode=db)，在本应用里也不会被实例化，所以缺 mapper 不影响启动。
        "com.ghb.ecommerceflashsalesystem.product.mapper",
        "com.ghb.ecommerceflashsalesystem.activity.mapper",
        "com.ghb.ecommerceflashsalesystem.order.mapper",
        "com.ghb.ecommerceflashsalesystem.analytics.mapper"
})
@EnableScheduling
@SpringBootApplication
public class ECommerceFlashSaleSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceFlashSaleSystemApplication.class, args);
    }

}
