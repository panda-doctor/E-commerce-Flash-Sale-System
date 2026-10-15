-- =====================================================
-- P3 Day 3：activity 域独立 schema（数据所有权）
-- 执行：mysql --default-character-set=utf8mb4 --execute="source <本文件>"
-- 注意：旧库 seckill_activity 上的 fk_activity_product（→ product.id）是**跨域外键**，
--       拆域后不再存在（MySQL 外键约束只在同一 schema 内维护）；
--       完整性由应用层保证（创建活动时校验商品存在）。
-- =====================================================
CREATE DATABASE IF NOT EXISTS flash_sale_activity
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS flash_sale_activity.seckill_activity LIKE flash_sale.seckill_activity;
