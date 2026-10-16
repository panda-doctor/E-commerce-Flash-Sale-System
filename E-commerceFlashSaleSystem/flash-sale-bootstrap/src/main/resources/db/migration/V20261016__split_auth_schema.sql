-- =============================================================================
-- P4 Day 6：auth 域「库」拆分 —— 新建权威 schema flash_sale_auth
-- =============================================================================
-- 与 P3 Day 3（product/activity）、P4 Day 4（order）同一手法：把 auth 域两张表搬到
-- 独立 schema，**新 schema = 权威**（auth 应用读写），旧库 flash_sale 降级为**副本**
-- （monolith / seckill / order 三个运行时继续用它做会话校验），由 AuthMirror 反向镜像保鲜。
--
-- 幂等：可重复执行（先 DROP 再建 + 全量 REPLACE）。请在**停服务**时执行（避免半程写入）。
--
-- ⚠️ 与前三块拆分唯一的差异：**带上那条外键**（fk_session_user: user_session.user_id →
--    user_account.id）。理由：① 它是真实不变量（不存在无主会话）；② auth 的写者只有一个
--    应用、迁移可以"先用户后会话"，不受镜像写入顺序拖累；③ Day 5 的教训 —— 外键在测试里
--    真的在干活（丢了外键，"坏数据"路径会静默成功）。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS flash_sale_auth
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 幂等重建（REPLACE 只会覆盖、不会删除源库已删的行，所以重跑前先清表）
DROP TABLE IF EXISTS flash_sale_auth.user_session;
DROP TABLE IF EXISTS flash_sale_auth.user_account;

-- 结构照抄（LIKE 含索引，不含外键 —— 外键下面显式补）
CREATE TABLE flash_sale_auth.user_account LIKE flash_sale.user_account;
CREATE TABLE flash_sale_auth.user_session LIKE flash_sale.user_session;

-- 全量搬迁（顺序：先用户后会话，满足外键）
REPLACE INTO flash_sale_auth.user_account SELECT * FROM flash_sale.user_account;
REPLACE INTO flash_sale_auth.user_session SELECT * FROM flash_sale.user_session;

-- 补外键（不复制自增/索引，只加约束；ON DELETE NO ACTION 与旧库一致）
ALTER TABLE flash_sale_auth.user_session
    ADD CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES flash_sale_auth.user_account (id);

-- ---------------------------------------------------------------------------
-- 校验（执行后人工核对输出）
-- ---------------------------------------------------------------------------
SELECT 'user_account' AS tbl,
       (SELECT COUNT(*) FROM flash_sale.user_account) AS legacy_rows,
       (SELECT COUNT(*) FROM flash_sale_auth.user_account) AS auth_rows
UNION ALL
SELECT 'user_session',
       (SELECT COUNT(*) FROM flash_sale.user_session),
       (SELECT COUNT(*) FROM flash_sale_auth.user_session);

SELECT CONCAT('flash_sale_auth 外键数=', COUNT(*)) AS fk_check
FROM information_schema.referential_constraints
WHERE constraint_schema = 'flash_sale_auth';
