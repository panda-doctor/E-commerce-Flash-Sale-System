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
@MapperScan("com.ghb.ecommerceflashsalesystem.activity.mapper")
// P4 Day 7：**user.mapper 已从扫描面移除** —— 会话校验跨服务化（SessionGuard ->
// SessionValidationPort -> auth 内部接口），本应用不再需要 auth 的两张表；
// 同时数据源从旧库切到**活动权威库 flash_sale_activity**（本应用只读活动）。
@SpringBootApplication
public class SeckillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillServiceApplication.class, args);
    }
}
