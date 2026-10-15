-- =====================================================
-- P3 Day 3：product 域独立 schema（数据所有权）
-- 执行：mysql --default-character-set=utf8mb4 --execute="source <本文件>"
-- 说明：CREATE TABLE ... LIKE 只复制列/索引/自增，不复制外键 ——
--       这正是拆域所需（跨域外键不应存在，完整性交应用层校验：
--       创建活动时校验商品存在、下单时校验活动/商品存在）。
-- 读路径本日未切换：各应用仍连 flash_sale，本库为"数据所有权"准备。
-- =====================================================
CREATE DATABASE IF NOT EXISTS flash_sale_product
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS flash_sale_product.product LIKE flash_sale.product;
