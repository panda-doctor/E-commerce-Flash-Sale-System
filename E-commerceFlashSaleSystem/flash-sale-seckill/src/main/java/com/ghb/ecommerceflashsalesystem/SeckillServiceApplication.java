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
 * <p><b>只读一个表</b>：{@code seckill_activity}（活动缓存失效时兜底读），所以
 * {@code @MapperScan} 只扫 {@code activity.mapper} —— 本应用对 {@code product} 表零访问
 * （商品信息经 {@code flash-sale-remote} 的 HTTP 适配器向商品服务取），对两表也**零写**
 * （订单落库由 order 域消费者在单体完成）。</p>
 *
 * <p><b>刻意不加 {@code @EnableScheduling}</b>：定时任务只存在于 order 域的 Stream 消费调度（单体）。</p>
 */
@MapperScan("com.ghb.ecommerceflashsalesystem.activity.mapper")
@SpringBootApplication
public class SeckillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillServiceApplication.class, args);
    }
}
