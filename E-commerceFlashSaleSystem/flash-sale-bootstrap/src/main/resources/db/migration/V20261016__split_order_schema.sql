-- =====================================================
-- P4 Day 4：order 独立 schema —— 建库建表 + 全量搬迁（幂等）
-- 执行：mysql --default-character-set=utf8mb4 --execute="source <本文件>"
-- 约定：与 V20261015__split_product_activity_schema.sql 同风格（当前手工执行，未接 Flyway）
-- 回滚：DROP DATABASE flash_sale_order;
--       （回滚 = 把 flash-sale-order 应用的 datasource 换回旧库 + 关镜像，见 plan.md P4 Day 4 卡）
-- 幂等：可重复执行；第二步用 REPLACE 以主键为准覆盖，结果一致
-- 注意：CREATE TABLE ... LIKE **不复制外键**（与 P3 Day 3 同）—— 权威库不需要跨域 FK；
--       反向镜像写旧库时仍受旧库 FK 约束（顺序天然满足：活动/商品先于订单）
-- =====================================================

-- 1) 建库建表（结构与旧库一致）
CREATE DATABASE IF NOT EXISTS flash_sale_order
  DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS flash_sale_order.seckill_order LIKE flash_sale.seckill_order;
CREATE TABLE IF NOT EXISTS flash_sale_order.seckill_message_log LIKE flash_sale.seckill_message_log;

-- 2) 全量搬迁（REPLACE 以主键为准覆盖：重复执行结果一致）
REPLACE INTO flash_sale_order.seckill_order
SELECT * FROM flash_sale.seckill_order;

REPLACE INTO flash_sale_order.seckill_message_log
SELECT * FROM flash_sale.seckill_message_log;

-- 3) 校验：src / dst 行数必须相等（人工核对输出）
SELECT 'seckill_order' AS tbl,
       (SELECT COUNT(*) FROM flash_sale.seckill_order) AS src,
       (SELECT COUNT(*) FROM flash_sale_order.seckill_order) AS dst
UNION ALL
SELECT 'seckill_message_log',
       (SELECT COUNT(*) FROM flash_sale.seckill_message_log),
       (SELECT COUNT(*) FROM flash_sale_order.seckill_message_log);
