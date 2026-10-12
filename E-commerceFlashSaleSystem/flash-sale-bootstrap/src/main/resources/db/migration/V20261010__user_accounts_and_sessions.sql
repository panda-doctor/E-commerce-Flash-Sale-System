USE flash_sale;

CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT UNSIGNED NOT NULL COMMENT '用户编号',
  account_name VARCHAR(32) NOT NULL COMMENT '规范化账号名',
  password_hash VARCHAR(100) NOT NULL COMMENT '单向密码哈希',
  display_name VARCHAR(64) DEFAULT NULL COMMENT '用户昵称',
  status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1正常，2已删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_account_name (account_name),
  KEY idx_user_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

CREATE TABLE IF NOT EXISTS user_session (
  token_hash CHAR(64) NOT NULL COMMENT '随机会话令牌的SHA-256摘要',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户编号',
  csrf_token_hash CHAR(64) NOT NULL COMMENT '跨站请求伪造令牌摘要',
  expires_at DATETIME NOT NULL COMMENT '过期时间',
  revoked_at DATETIME DEFAULT NULL COMMENT '撤销时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (token_hash),
  KEY idx_session_user_expiry (user_id, expires_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可撤销用户会话';
