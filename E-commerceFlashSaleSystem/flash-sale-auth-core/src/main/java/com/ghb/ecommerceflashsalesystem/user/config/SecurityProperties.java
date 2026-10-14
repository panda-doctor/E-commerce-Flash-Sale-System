package com.ghb.ecommerceflashsalesystem.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户会话、浏览器跨域来源与管理令牌配置。
 *
 * <p>对应 application.yaml 的 {@code security.*}，支持 .env 环境变量覆盖：
 * <ul>
 *   <li>{@code ADMIN_TOKEN} → admin-token：管理端请求头 {@code X-Admin-Token} 的期望值；</li>
 *   <li>{@code USER_TOKENS} → user-tokens：历史压测客户端使用的预配置令牌白名单。</li>
 * </ul>
 * 普通用户优先使用持久化的浏览器会话。
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /** 管理端令牌（X-Admin-Token），为空则 /api/admin/** 一律拒绝 */
    private String adminToken = "";

    /** 预配置压测令牌白名单：token:userId，多组用英文逗号分隔。 */
    private String userTokens = "";
    private boolean legacyUserTokensEnabled = false;

    private String sessionCookieName = "FLASH_SESSION";
    private boolean sessionCookieSecure = false;
    private long sessionTtlHours = 168;
    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

    public java.util.List<String> getAllowedOriginList() {
        return java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
