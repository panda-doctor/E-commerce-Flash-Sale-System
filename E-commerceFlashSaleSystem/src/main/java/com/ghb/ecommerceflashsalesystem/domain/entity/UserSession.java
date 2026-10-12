package com.ghb.ecommerceflashsalesystem.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_session")
public class UserSession {
    /** 原始令牌不落库，仅保存 SHA-256 摘要。 */
    @TableId
    private String tokenHash;
    private Long userId;
    private String csrfTokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
