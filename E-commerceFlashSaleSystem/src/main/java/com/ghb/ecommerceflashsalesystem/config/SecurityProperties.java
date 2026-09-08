package com.ghb.ecommerceflashsalesystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 演示系统的轻量访问控制配置。密钥只允许从环境变量注入，不能提交进仓库。
 * user-tokens 格式：token:用户ID,token2:用户ID，例如 token-a:1001,token-b:1002。
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    private String adminToken = "";
    private String userTokens = "";
}
