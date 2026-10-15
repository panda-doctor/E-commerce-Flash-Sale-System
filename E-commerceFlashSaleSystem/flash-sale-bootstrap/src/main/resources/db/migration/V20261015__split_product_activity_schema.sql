-- =====================================================
-- P3 Day 3：product / activity 独立 schema —— 建库建表 + 全量搬迁（幂等）
-- 执行：mysql --default-character-set=utf8mb4 --execute="source <本文件>"
-- 约定：与 db/migration/V20261010__user_accounts_and_sessions.sql 同名风格（当前手工执行，未接 Flyway）
-- 回滚：DROP DATABASE flash_sale_product; DROP DATABASE flash_sale_activity;
--       （本日未切换读路径 —— 各应用仍连 flash_sale —— 回滚对业务无感）
-- 幂等：可重复执行；第二步用 REPLACE 以主键为准覆盖，结果一致
-- =====================================================

-- 1) 建库建表（结构与旧库一致；LIKE 不复制跨域外键）
CREATE DATABASE IF NOT EXISTS flash_sale_product
  DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS flash_sale_activity
  DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS flash_sale_product.product LIKE flash_sale.product;
CREATE TABLE IF NOT EXISTS flash_sale_activity.seckill_activity LIKE flash_sale.seckill_activity;

-- 2) 全量搬迁（REPLACE 以主键为准覆盖：重复执行结果一致）
REPLACE INTO flash_sale_product.product
SELECT * FROM flash_sale.product;

REPLACE INTO flash_sale_activity.seckill_activity
SELECT * FROM flash_sale.seckill_activity;

-- 3) 校验：src / dst 行数必须相等（人工核对输出）
SELECT 'product' AS tbl,
       (SELECT COUNT(*) FROM flash_sale.product) AS src,
       (SELECT COUNT(*) FROM flash_sale_product.product) AS dst
UNION ALL
SELECT 'seckill_activity',
       (SELECT COUNT(*) FROM flash_sale.seckill_activity),
       (SELECT COUNT(*) FROM flash_sale_activity.seckill_activity);
