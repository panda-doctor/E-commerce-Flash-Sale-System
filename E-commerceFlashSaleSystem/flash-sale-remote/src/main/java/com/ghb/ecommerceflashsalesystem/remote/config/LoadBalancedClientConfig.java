package com.ghb.ecommerceflashsalesystem.remote.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 服务间调用客户端配置（P3 Day 5 引入，P4 Day 2 随适配器一起搬进 flash-sale-remote 共享）。
 *
 * <p>用 {@code @LoadBalanced RestClient.Builder}：URI 里写 {@code http://flash-sale-monolith/...}
 * 这样的<b>服务名</b>，由 Spring Cloud LoadBalancer + Nacos 解析成实例地址（多实例自动轮询），
 * 不写死 IP:端口。</p>
 *
 * <p>用显式 Bean 名 + 限定注入，避免与 Boot 自动装配的默认 {@code RestClient.Builder} 冲突。</p>
 */
@Configuration
public class LoadBalancedClientConfig {

    /** 负载均衡的 RestClient.Builder（解析 {@code http://<服务名>} 形式的 URI）。 */
    @Bean("loadBalancedRestClientBuilder")
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    /** 服务间调用用的 RestClient（内部接口：库存写入、商品摘要）。 */
    @Bean
    RestClient internalServiceRestClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
        return builder.build();
    }
}
