package com.ghb.ecommerceflashsalesystem.infra.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端访问令牌配置（P3 Day 5：下沉到 infra —— 让每个对外服务都自带 {@code /api/admin/**} 纵深防御）。
 *
 * <p>与 auth-core 的 {@code SecurityProperties} 使用<b>同一个配置键</b>
 * {@code security.admin-token}：单体同时存在两处校验，但规则与结果完全一致（P4 收敛为一处），
 * 而拆出去的应用（product / activity）从此不再"只靠网关把关"（P3 Day 4 记录的缺口）。</p>
 *
 * <p><b>fail-closed</b>：令牌未配置时拒绝所有管理端请求 —— 避免"忘了配令牌 = 后台裸奔"。</p>
 */
@Data
@ConfigurationProperties(prefix = "security")
public class AdminTokenProperties {

    /** 管理端访问令牌（请求头 {@code X-Admin-Token}）。未配置时管理端接口一律拒绝。 */
    private String adminToken;
}
