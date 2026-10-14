package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动类（P2 边缘抽取 · Day 1 骨架）。
 *
 * <p>定位：把用户注册 / 登录 / 会话 / CSRF / 令牌从模块化单体中拆成**独立进程**，
 * 端口 8082，注册进 Nacos 的服务名为 {@code flash-sale-auth}；P2 收尾后由网关
 * 统一鉴权并路由 {@code /api/auth/**}、{@code /api/admin/users/**} 到本服务。</p>
 *
 * <p>Day 1 只做骨架、零行为变更：本模块依赖 {@code flash-sale-user} 库，
 * 因而暂时也持有该库内的 controller/service/mapper（与单体重复暴露，但网关尚未
 * 路由到本服务，线上行为不变）。Day 2 起把域代码迁入本模块、并从单体摘除。</p>
 *
 * <p>启动类必须位于 {@code com.ghb.ecommerceflashsalesystem} 根包下，
 * 组件扫描才能覆盖共享库（infra 的 Redis/MyBatis 配置、user 域的 Bean）。</p>
 */
@MapperScan("com.ghb.ecommerceflashsalesystem.user.mapper")
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
