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
 * <p>本应用是活动域（{@code seckill_activity}）的**唯一权威**：读写都指向 {@code flash_sale_activity}。
 * P4 收官后旧库 {@code flash_sale} 已下线，反向镜像（{@code DualWriteMirror}）与双写开关一并删除。</p>
 */
// P3 Day 5：只扫活动域自己的 mapper —— 商品存在性校验已改为调用 product 服务的内部接口
// （ProductCatalogPort + HttpProductCatalogAdapter），应用不再直读 product 表
// （这也是 P4 首日"读切换到新 schema"的前置条件：新库里没有 product 表）。
@MapperScan("com.ghb.ecommerceflashsalesystem.activity.mapper")
@SpringBootApplication
public class ActivityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityServiceApplication.class, args);
    }
}
