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
    [string]$Schema = 'flash_sale_test',
    [string]$Source = 'flash_sale'
)

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

Write-Output "=== 建/刷新测试库 $Schema（源：$Source）==="
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
    Invoke-Mysql "SET FOREIGN_KEY_CHECKS=0; DROP TABLE IF EXISTS $Schema.$t;"
    # LIKE 只复制结构（含索引），不复制外键 —— 与 P3/P4 的 schema 拆分同一手法
    Invoke-Mysql "CREATE TABLE $Schema.$t LIKE $Source.$t;"
    Invoke-Mysql "INSERT INTO $Schema.$t SELECT * FROM $Source.$t;"
}

# ---------------------------------------------------------------------------
# 恢复外键：LIKE 不复制外键，但**测试用例依赖它们** —— SeckillMessageReliabilityTest 的
# "坏活动/坏商品"失败路径正是靠 `seckill_order` 的 FK 失败来制造 FAILED/DEAD；
# 丢了外键这些用例会"意外成功"，断言 expected: 2 but was: 1（实测踩到）。
# 做法：从 information_schema 生成 ALTER 语句（含原有 ON DELETE/UPDATE 规则），
# 在数据导入**之后**执行（这样插入顺序不受约束）。
# ---------------------------------------------------------------------------
$fkQuery = @"
SELECT CONCAT('ALTER TABLE $Schema.', k.table_name, ' ADD CONSTRAINT ', k.constraint_name,
       ' FOREIGN KEY (', GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position), ') REFERENCES $Schema.',
       k.referenced_table_name, ' (', GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position), ')',
       ' ON DELETE ', rc.delete_rule, ' ON UPDATE ', rc.update_rule, ';') AS ddl
FROM information_schema.key_column_usage k
JOIN information_schema.referential_constraints rc
  ON rc.constraint_schema = k.table_schema AND rc.constraint_name = k.constraint_name
WHERE k.table_schema = '$Source' AND k.referenced_table_name IS NOT NULL
GROUP BY k.table_name, k.constraint_name, k.referenced_table_name, rc.delete_rule, rc.update_rule;
"@
$fkRows = & $mysql '--host=127.0.0.1' '--user=root' '--skip-column-names' "--execute=$fkQuery;" 2>&1
$fkCount = 0
foreach ($row in $fkRows) {
    $ddl = ($row -split ';')[0].Trim()
    if ($ddl) { Invoke-Mysql $ddl | Out-Null; $fkCount++ }
}
Write-Output "  已恢复外键约束: $fkCount 条"

Write-Output '=== 快照校验（源库 vs 测试库行数）==='
$sql = ($tables | ForEach-Object {
        "SELECT '$_' AS t, (SELECT COUNT(*) FROM $Source.$_) AS src, (SELECT COUNT(*) FROM $Schema.$_) AS test"
    }) -join ' UNION ALL '
& $mysql '--host=127.0.0.1' '--user=root' '--skip-column-names' "--execute=$sql;" 2>&1 | ForEach-Object {
    $p = ($_ -split "`t")
    $flag = if ($p[1] -eq $p[2]) { '✓' } else { '✗' }
    '  {0,-26} 源={1,-6} 测试={2,-6} {3}' -f $p[0], $p[1], $p[2], $flag
}
Write-Output '  （测试库与业务库结构一致即可；行数一致说明快照是最新的）'
