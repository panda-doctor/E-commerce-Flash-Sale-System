package com.ghb.ecommerceflashsalesystem.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关入口（P1-1）。
 *
 * <p>职责：统一入口（前端只连网关）+ 静态路由到单体 + traceId 注入 + CORS。
 * P1-2 引入 Nacos 注册中心后，路由由静态 URI 切换为服务发现（lb://）。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
