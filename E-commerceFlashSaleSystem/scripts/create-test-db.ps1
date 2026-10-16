<#
  P4 Day 5：为 flash-sale-bootstrap 的集成测试准备**独立测试库**（默认 flash_sale_test）。

  为什么需要
  ----------
  单体（bootstrap）的集成测试用的是主 yaml 的数据源（= 业务库）。而消费类测试（注入
  SeckillOrderConsumer 自行驱动消费）会**扫真实 Redis Stream** 并把结果写进库 —— 在
  P4 Day 4 把 order 的权威切到 flash_sale_order 之后，这些写入会只落在旧库（副本），
  制造出对账看不到的"影子行"（Day 4 收尾实测到 23 行）。把测试数据源指到本库后，
  回归不再改动任何业务库，`scripts/reconcile-product-activity.ps1` 的门禁也才可信。

  做法
  ----
  建库 + 逐表 **DROP + `CREATE TABLE ... LIKE`（结构，含索引；不复制外键）+ `INSERT ... SELECT`**
  （全量快照）。**幂等且"全新"**：可重复执行，每次重建干净快照（业务数据漂移后跑一次即可）。

  注意（诚实说明）
  ----------------
  Redis 仍是共享的：测试依旧会消费真实 Stream 的消息（只是结果写进测试库）。
  隔离 Redis（Testcontainers 或独立 database 索引）列入 Day 6，见 docs/plan.md。
#>
param(
    [string]$Schema = 'flash_sale_test'
)

# P4 Day 10 · Step C：旧库 flash_sale 已下线 → 测试库改为**按表从各自权威库**取快照。
$sourceOf = @{
    'product'                   = 'flash_sale_product'
    'seckill_activity'          = 'flash_sale_activity'
    'seckill_order'             = 'flash_sale_order'
    'seckill_message_log'       = 'flash_sale_order'
    'seckill_activity_snapshot' = 'flash_sale_order'
    'user_account'              = 'flash_sale_auth'
    'user_session'              = 'flash_sale_auth'
}

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# MySQL 客户端：优选用项目约定路径，找不到再退回 PATH
$mysql = 'D:\MySQL\mysql-8.0.34-winx64\bin\mysql.exe'
if (-not (Test-Path $mysql)) { $mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source }
if (-not $mysql) { throw '未找到 mysql 客户端（请把 mysql.exe 加入 PATH 或修正本脚本顶部路径）' }

# 口令与其它脚本同一约定：从 .env 读 MYSQL_PASSWORD
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    $line = Get-Content $envFile -Encoding UTF8 | Where-Object { $_ -match '^MYSQL_PASSWORD=' } | Select-Object -First 1
    if ($line) { $env:MYSQL_PWD = ($line -replace '^MYSQL_PASSWORD=', '').Trim() }
}

$tables = @(
    'product', 'seckill_activity', 'seckill_order', 'seckill_message_log',
    'seckill_activity_snapshot', 'user_account', 'user_session'
)

function Invoke-Mysql([string]$sql) {
    & $mysql '--host=127.0.0.1' '--user=root' "--execute=$sql" 2>&1 | ForEach-Object { '    ' + $_ }
}

Write-Output "=== 建/刷新测试库 $Schema（源：四个权威库，逐表映射）==="
Invoke-Mysql "CREATE DATABASE IF NOT EXISTS $Schema DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

foreach ($t in $tables) {
    # ⚠️ 必须 DROP + CREATE，而不是 CREATE IF NOT EXISTS + REPLACE：
    # REPLACE 只覆盖"源库里有"的行，**不会删除**上一轮测试遗留的行 ——
    # 实测踩到：上一轮测试写的 23 行留在测试库，导致依赖行数/状态断言的用例变红。
    # ⚠️ 必须先关外键检查再 DROP：脚本尾部会**恢复外键**（Day 5 的修复），于是第二次以后的
    # 重建就会撞上 "Cannot drop table 'product' referenced by a foreign key constraint"
    # （实测：fk_activity_product 挡住 DROP product —— DROP 顺序无法同时满足所有父子关系）。
    # 注意 SET FOREIGN_KEY_CHECKS 是**会话级**的，而 Invoke-Mysql 每次都是独立的 mysql.exe
    # 进程 → 必须和 DROP 写在**同一条** --execute 里才生效。
    # 每张表的权威库不同（旧库已下线，不能再当唯一来源）
    $src = $sourceOf[$t]
    Invoke-Mysql "SET FOREIGN_KEY_CHECKS=0; DROP TABLE IF EXISTS $Schema.$t;"
    # LIKE 只复制结构（含索引），不复制外键 —— 与 P3/P4 的 schema 拆分同一手法
    Invoke-Mysql "CREATE TABLE $Schema.$t LIKE $src.$t;"
    Invoke-Mysql "INSERT INTO $Schema.$t SELECT * FROM $src.$t;"
}

# ---------------------------------------------------------------------------
# 恢复外键（P4 Day 10 · Step C 起改为**脚本内显式声明**）：
# 原先从源库 information_schema 反查，但 ① 权威四库是"零外键"schema（P3/P4 拆分手法），
# ② 旧库已下线 —— 再没有地方能"反查"出这些约束。而测试用例**依赖它们**
# （SeckillMessageReliabilityTest 的坏商品/坏活动失败路径正是靠 FK 失败制造 FAILED/DEAD）。
# 约束形状取自旧库下线前的逻辑备份（_legacy-backup/flash_sale_20261016.sql）：
# 5 条外键的 DEL/UPDATE 规则实测全为 NO ACTION（= 省略 ON 子句时的 MySQL 默认），故此处不写 ON。
# ---------------------------------------------------------------------------
$fkDdl = @(
    'ALTER TABLE {0}.seckill_activity ADD CONSTRAINT fk_activity_product FOREIGN KEY (product_id) REFERENCES {0}.product (id);'
    'ALTER TABLE {0}.seckill_activity_snapshot ADD CONSTRAINT fk_snapshot_activity FOREIGN KEY (activity_id) REFERENCES {0}.seckill_activity (id);'
    'ALTER TABLE {0}.seckill_order ADD CONSTRAINT fk_order_activity FOREIGN KEY (activity_id) REFERENCES {0}.seckill_activity (id);'
    'ALTER TABLE {0}.seckill_order ADD CONSTRAINT fk_order_product FOREIGN KEY (product_id) REFERENCES {0}.product (id);'
    'ALTER TABLE {0}.user_session ADD CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES {0}.user_account (id);'
)
$fkCount = 0
foreach ($tpl in $fkDdl) { Invoke-Mysql ($tpl -f $Schema) | Out-Null; $fkCount++ }
Write-Output "  已恢复外键约束: $fkCount 条"

Write-Output '=== 快照校验（各权威库 vs 测试库行数）==='
foreach ($t in $tables) {
    $src = $sourceOf[$t]
    $q1 = "SELECT COUNT(*) FROM $src.$t"
    $q2 = "SELECT COUNT(*) FROM $Schema.$t"
    $c1 = (& $mysql '--host=127.0.0.1' '--user=root' '--skip-column-names' "--execute=$q1;" 2>&1 | Out-String).Trim()
    $c2 = (& $mysql '--host=127.0.0.1' '--user=root' '--skip-column-names' "--execute=$q2;" 2>&1 | Out-String).Trim()
    $flag = if ($c1 -eq $c2) { '✓' } else { '✗' }
    '  {0,-26} 源={1,-6} 测试={2,-6} {3}' -f $t, $c1, $c2, $flag
}
Write-Output '  （源=各表权威库；行数一致说明快照是最新的）'
