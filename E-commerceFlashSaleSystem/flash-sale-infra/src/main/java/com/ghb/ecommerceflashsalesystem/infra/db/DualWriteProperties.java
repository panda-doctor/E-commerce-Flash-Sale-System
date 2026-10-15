package com.ghb.ecommerceflashsalesystem.infra.db;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * P4 Day 1「切读」后的「域库镜像」配置（**过渡期专用**）。
 *
 * <p><b>语义已随切读反转</b>（Day 3 写这份类时方向相反，务必留意）：</p>
 * <ul>
 *   <li><b>权威源 = 新 schema</b>（{@code flash_sale_product} / {@code flash_sale_activity}）：
 *       两个已拆应用的 `spring.datasource` 指向它，读 + 写都在这里；</li>
 *   <li><b>旧库 {@code flash_sale} = 副本</b>：降级为**未拆模块**（seckill / ai / analytics，
 *       仍在单体）的只读来源，由本镜像在权威写入后保持新鲜。</li>
 * </ul>
 *
 * <p><b>为什么 {@code enabled} 仍是"默认关"</b>：这是"安全默认值" —— 没配
 * {@code module.db.dual-write} 的进程（单体 / ai）不会误写第二个库。而**两个已拆应用的 yaml
 * 显式开成默认 true**（{@code ${DUAL_WRITE_ENABLED:true}}），因为它们才是副本的维护者：
 * 关掉 = 未拆模块会读到凝固的旧副本。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "module.db.dual-write")
public class DualWriteProperties {

    /** 镜像总开关：类内默认关闭（安全默认值），由两个已拆应用的 yaml 显式开启 */
    private boolean enabled = false;

    /** **副本**库（旧库 {@code flash_sale}）里 product 域表所在的 schema */
    private String legacyProductUrl = "jdbc:mysql://localhost:3306/flash_sale";

    /** **副本**库（旧库 {@code flash_sale}）里 activity 域表所在的 schema */
    private String legacyActivityUrl = "jdbc:mysql://localhost:3306/flash_sale";

    /** 副本库账号（过渡期与权威库同实例、同账号） */
    private String username = "root";

    /** 副本库密码（默认从 .env 的 MYSQL_PASSWORD 注入） */
    private String password = "";
}
