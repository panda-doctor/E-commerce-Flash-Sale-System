package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 活动服务启动类（P3 Day 4：activity 域服务化）。
 *
 * <p>端口 8085、注册名 flash-sale-activity。启动类必须位于根包
 * {@code com.ghb.ecommerceflashsalesystem}，组件扫描才能覆盖共享库 Bean
 * （infra 的 Redis / Redisson / MyBatis-Plus 配置与全局异常处理，activity-core 的
 * {@code SeckillActivityService} / {@code SeckillCacheService} 等）。</p>
 *
 * <p><b>刻意不加 {@code @EnableScheduling}</b>：单体里的定时任务是 flash-sale-order 的
 * Stream 消费调度（{@code StreamConsumerScheduler}，fixedDelay 150ms/5s）；本服务不依赖
 * order 模块，开了无任务可跑，反而有"库里定时任务被重复执行"的风险。</p>
 *
 * <p>本日只承载活动自己的管理端接口（建/改活动、预热、重置库存）；<b>读路径仍指旧库
 * {@code flash_sale}</b>，写路径经 {@code DualWriteMirror} 镜像到 {@code flash_sale_activity}
 * （Day 3 已有的双写开关，默认关闭）。</p>
 */
@MapperScan("com.ghb.ecommerceflashsalesystem.activity.mapper")
@SpringBootApplication
public class ActivityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityServiceApplication.class, args);
    }
}
