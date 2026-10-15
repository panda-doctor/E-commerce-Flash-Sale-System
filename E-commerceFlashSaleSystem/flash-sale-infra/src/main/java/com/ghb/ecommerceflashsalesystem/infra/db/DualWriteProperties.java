package com.ghb.ecommerceflashsalesystem.infra.db;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * P3 Day 3：双写镜像配置（**过渡期专用**，默认关闭）。
 *
 * <p>背景：product / activity 已建立独立 schema（{@code flash_sale_product} / {@code flash_sale_activity}），
 * 但读路径仍指向旧库 {@code flash_sale}。为保证"把读路径切到新库"那天数据不丢，
 * 过渡期内所有写操作在主库提交后**镜像**一份到新库。</p>
 *
 * <p>为什么默认 {@code false}：没准备好的镜像会写出脏数据，且会给写路径引入额外延迟；
 * 必须由显式的配置/环境变量打开（{@code DUAL_WRITE_ENABLED=true}）。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "module.db.dual-write")
public class DualWriteProperties {

    /** 总开关：默认关闭 */
    private boolean enabled = false;

    /** product 域镜像库（新 schema） */
    private String productUrl = "jdbc:mysql://localhost:3306/flash_sale_product";

    /** activity 域镜像库（新 schema） */
    private String activityUrl = "jdbc:mysql://localhost:3306/flash_sale_activity";

    /** 镜像库账号（与主库一致，过渡期同实例） */
    private String username = "root";

    /** 镜像库密码（默认从 .env 的 MYSQL_PASSWORD 注入） */
    private String password = "";
}
