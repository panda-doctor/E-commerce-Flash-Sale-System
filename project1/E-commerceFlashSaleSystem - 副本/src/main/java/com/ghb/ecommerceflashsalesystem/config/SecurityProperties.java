package com.ghb.ecommerceflashsalesystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 访问令牌安全配置（审计整改 R3 引入；动态发令牌增强后，静态白名单仍保留六演示账号）。
 *
 * <p>对应 application.yaml 的 {@code security.*}，支持 .env 环境变量覆盖：
 * <ul>
 *   <li>{@code ADMIN_TOKEN} → admin-token：管理端请求头 {@code X-Admin-Token} 的期望值；</li>
 *   <li>{@code USER_TOKENS} → user-tokens：静态令牌白名单，格式 {@code token:userId,...}（英文逗号分隔）。</li>
 * </ul>
 * 动态注册的用户令牌不在此配置内，由 {@link UserTokenRegistry} 在内存中维护。
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /** 管理端令牌（X-Admin-Token），为空则 /api/admin/** 一律拒绝 */
    private String adminToken = "";

    /** 静态用户令牌白名单：token:userId，多组用英文逗号分隔（如 user-a:1001,user-b:1002） */
    private String userTokens = "";
}
