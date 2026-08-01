CREATE DATABASE IF NOT EXISTS flash_sale
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE flash_sale;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT UNSIGNED NOT NULL COMMENT '商品编号',
  name VARCHAR(128) NOT NULL COMMENT '商品名称',
  description VARCHAR(512) DEFAULT NULL COMMENT '商品描述',
  image_url VARCHAR(512) DEFAULT NULL COMMENT '商品图片地址',
  original_price BIGINT UNSIGNED NOT NULL COMMENT '原价，单位分',
  total_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '商品总库存',
  available_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '普通可用库存',
  status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0下架，1上架',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_product_status (status),
  KEY idx_product_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

CREATE TABLE IF NOT EXISTS seckill_activity (
  id BIGINT UNSIGNED NOT NULL COMMENT '秒杀活动编号',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '商品编号',
  activity_name VARCHAR(128) NOT NULL COMMENT '活动名称',
  start_time DATETIME NOT NULL COMMENT '开始时间',
  end_time DATETIME NOT NULL COMMENT '结束时间',
  seckill_price BIGINT UNSIGNED NOT NULL COMMENT '秒杀价，单位分',
  seckill_stock INT UNSIGNED NOT NULL COMMENT '秒杀库存',
  limit_per_user INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每个用户限购数量',
  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0未开始，1进行中，2已结束，3已售罄，4已取消',
  preheat_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '预热状态：0未预热，1已预热，2预热失败',
  version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '版本号，用于后台更新控制',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_activity_product_id (product_id),
  KEY idx_activity_time_status (start_time, end_time, status),
  KEY idx_activity_status (status),
  CONSTRAINT fk_activity_product
    FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动表';

CREATE TABLE IF NOT EXISTS seckill_order (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单主键',
  order_no VARCHAR(40) NOT NULL COMMENT '订单编号',
  activity_id BIGINT UNSIGNED NOT NULL COMMENT '秒杀活动编号',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '商品编号',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户编号',
  seckill_price BIGINT UNSIGNED NOT NULL COMMENT '成交价，单位分',
  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0排队中，1已创建，2创建失败，3已取消',
  stream_message_id VARCHAR(64) DEFAULT NULL COMMENT '缓存消息编号',
  failure_reason VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_activity_user (activity_id, user_id),
  KEY idx_order_user_id (user_id),
  KEY idx_order_activity_status (activity_id, status),
  KEY idx_order_created_at (created_at),
  CONSTRAINT fk_order_activity
    FOREIGN KEY (activity_id) REFERENCES seckill_activity (id),
  CONSTRAINT fk_order_product
    FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀订单表';

CREATE TABLE IF NOT EXISTS seckill_message_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息日志主键',
  stream_key VARCHAR(128) NOT NULL COMMENT '消息流键',
  stream_message_id VARCHAR(64) NOT NULL COMMENT '消息编号',
  consumer_group VARCHAR(64) DEFAULT NULL COMMENT '消费者组',
  consumer_name VARCHAR(64) DEFAULT NULL COMMENT '消费者名称',
  order_no VARCHAR(40) DEFAULT NULL COMMENT '订单编号',
  activity_id BIGINT UNSIGNED NOT NULL COMMENT '秒杀活动编号',
  product_id BIGINT UNSIGNED NOT NULL COMMENT '商品编号',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户编号',
  retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待消费，1消费成功，2消费失败，3已进入死信',
  error_message VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stream_message (stream_key, stream_message_id),
  KEY idx_message_activity_user (activity_id, user_id),
  KEY idx_message_status_retry (status, retry_count),
  KEY idx_message_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀消息消费日志表';

CREATE TABLE IF NOT EXISTS seckill_activity_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '快照主键',
  activity_id BIGINT UNSIGNED NOT NULL COMMENT '秒杀活动编号',
  redis_stock INT DEFAULT NULL COMMENT '缓存库存',
  order_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单数量',
  queued_message_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '队列积压消息数',
  success_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功数量',
  duplicate_reject_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重复请求拒绝数量',
  rate_limit_reject_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '限流拒绝数量',
  sold_out_reject_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '售罄拒绝数量',
  snapshot_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
  PRIMARY KEY (id),
  KEY idx_snapshot_activity_time (activity_id, snapshot_time),
  CONSTRAINT fk_snapshot_activity
    FOREIGN KEY (activity_id) REFERENCES seckill_activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动指标快照表';

INSERT INTO product (
  id,
  name,
  description,
  image_url,
  original_price,
  total_stock,
  available_stock,
  status
) VALUES (
  1,
  '秒杀机械键盘',
  '高并发秒杀测试商品',
  NULL,
  29900,
  1000,
  1000,
  1
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  original_price = VALUES(original_price),
  total_stock = VALUES(total_stock),
  available_stock = VALUES(available_stock),
  status = VALUES(status);

INSERT INTO seckill_activity (
  id,
  product_id,
  activity_name,
  start_time,
  end_time,
  seckill_price,
  seckill_stock,
  limit_per_user,
  status,
  preheat_status
) VALUES (
  1,
  1,
  '键盘限时秒杀',
  '2026-07-27 20:00:00',
  '2026-07-27 21:00:00',
  9900,
  100,
  1,
  0,
  0
) ON DUPLICATE KEY UPDATE
  product_id = VALUES(product_id),
  activity_name = VALUES(activity_name),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  seckill_price = VALUES(seckill_price),
  seckill_stock = VALUES(seckill_stock),
  limit_per_user = VALUES(limit_per_user),
  status = VALUES(status),
  preheat_status = VALUES(preheat_status);
