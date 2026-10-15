package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀执行域应用（P4 Day 2，端口 8086）。
 *
 * <p>承载 4 个 HTTP 入口（原在单体，路径不变）：公开读 {@code GET /api/seckill/activities}、
 * {@code GET /api/seckill/activities/{id}} 与需登录的 {@code GET /api/seckill/activities/{id}/check}、
 * {@code POST /api/seckill/execute}。</p>
 *
 * <p><b>两个 mapper 包</b>：{@code activity.mapper} 是活动读（缓存失效时兜底，本应用对两表零写）；
 * {@code user.mapper} 是 auth-core 的 {@code UserAccountService} 需要的（本应用带 auth-core 是为了
 * 用户身份校验，与单体同一套机制）—— 用户表只存在于旧库，因此 yaml 里数据源也指旧库。</p>
 *
 * <p><b>刻意不加 {@code @EnableScheduling}</b>：定时任务只存在于 order 域的 Stream 消费调度（单体）。</p>
 */
@MapperScan({"com.ghb.ecommerceflashsalesystem.activity.mapper",
        "com.ghb.ecommerceflashsalesystem.user.mapper"})
@SpringBootApplication
public class SeckillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillServiceApplication.class, args);
    }
}
