<#
.SYNOPSIS
  P3 Day 3: product / activity schema reconciliation (transitional dual-write consistency check).

.DESCRIPTION
  Compares legacy database flash_sale against the new domain schemas
  flash_sale_product / flash_sale_activity:
    1) row counts
    2) business aggregates (stock sums / preheat distribution)
    3) content fingerprint (MD5 over business columns, timestamp-free)
  Any difference -> prints details and exits with code 1 (can be used as a gate
  before switching the read path). All equal -> exits 0.

  NOTE: keep this file UTF-8 **with BOM** (Windows PowerShell 5.1 reads .ps1 as ANSI/GBK
  when there is no BOM, which corrupts non-ASCII text and can break parsing).

.EXAMPLE
  .\scripts\reconcile-product-activity.ps1
  .\scripts\reconcile-product-activity.ps1 -RepoRoot D:\Project\E-commerceFlashSaleSystem
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$MysqlExe = 'D:\MySQL\mysql-8.0.34-winx64\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [string]$DbUser = 'root'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $MysqlExe)) { Write-Error "mysql client not found: $MysqlExe (use -MysqlExe)"; exit 2 }

# ---- 1. read MYSQL_PASSWORD from .env (only into MYSQL_PWD, never on the command line) ----
$envCandidates = @(
    (Join-Path $RepoRoot 'E-commerceFlashSaleSystem\.env'),
    (Join-Path $RepoRoot '.env')
)
$envFile = $envCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $envFile) { Write-Error "cannot find .env (tried: $($envCandidates -join ' , '))"; exit 2 }
$line = Get-Content $envFile -Encoding UTF8 | Where-Object { $_ -match '^MYSQL_PASSWORD=' } | Select-Object -First 1
if (-not $line) { Write-Error "$envFile has no MYSQL_PASSWORD"; exit 2 }
$env:MYSQL_PWD = ($line -replace '^MYSQL_PASSWORD=', '').Trim()

# ---- 2. scalar query helper ----
function Invoke-Scalar([string]$sql) {
    $out = & $MysqlExe "--host=$DbHost" "--user=$DbUser" '--skip-column-names' '--default-character-set=utf8mb4' "--execute=$sql" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "SQL failed (exit=$LASTEXITCODE): $sql`n$out" }
    return (($out | Out-String).Trim())
}

# ---- 3. checks: each item gives the same-shaped SQL for legacy vs new schema ----
# content fingerprint = MD5 over business columns ordered by id (timestamps excluded on purpose:
# a write + its mirror can straddle a second boundary, which would create false positives)
$productFp = "SELECT MD5(GROUP_CONCAT(CONCAT_WS(',', id, name, IFNULL(description,''), IFNULL(image_url,''), original_price, total_stock, available_stock, status) ORDER BY id SEPARATOR '|')) FROM {0}.product"
$activityFp = "SELECT MD5(GROUP_CONCAT(CONCAT_WS(',', id, product_id, activity_name, start_time, end_time, seckill_price, seckill_stock, limit_per_user, status, preheat_status, version) ORDER BY id SEPARATOR '|')) FROM {0}.seckill_activity"
$preheatDist = 'SELECT CONCAT(SUM(preheat_status=0),CHAR(45),SUM(preheat_status=1),CHAR(45),SUM(preheat_status=2)) FROM {0}.seckill_activity'
# P4 Day 4：order 域（seckill_order / seckill_message_log）。指纹只取"末尾 12 行"（按 id 倒序）：
# 全表指纹会撞 group_concat_max_len(1024) 的静默截断；行数 + SUM 采样 + 小窗口指纹三者互补。
$orderTailFp = "SELECT MD5(GROUP_CONCAT(CONCAT_WS(',', id, order_no, activity_id, product_id, user_id, seckill_price, status, IFNULL(stream_message_id,''), IFNULL(failure_reason,'')) ORDER BY id SEPARATOR '|')) FROM (SELECT * FROM {0}.seckill_order ORDER BY id DESC LIMIT 12) t"
$logTailFp = "SELECT MD5(GROUP_CONCAT(CONCAT_WS(',', id, stream_key, stream_message_id, IFNULL(order_no,''), retry_count, status, IFNULL(error_message,'')) ORDER BY id SEPARATOR '|')) FROM (SELECT * FROM {0}.seckill_message_log ORDER BY id DESC LIMIT 12) t"
$logStatusDist = 'SELECT CONCAT(SUM(status=0),CHAR(45),SUM(status=1),CHAR(45),SUM(status=2),CHAR(45),SUM(status=3)) FROM {0}.seckill_message_log'

$checks = @(
    [pscustomobject]@{ Name = 'product.rows';        Old = 'SELECT COUNT(*) FROM flash_sale.product';                       New = 'SELECT COUNT(*) FROM flash_sale_product.product' }
    [pscustomobject]@{ Name = 'product.total_stock'; Old = 'SELECT IFNULL(SUM(total_stock),0) FROM flash_sale.product';       New = 'SELECT IFNULL(SUM(total_stock),0) FROM flash_sale_product.product' }
    [pscustomobject]@{ Name = 'product.avail_stock'; Old = 'SELECT IFNULL(SUM(available_stock),0) FROM flash_sale.product';   New = 'SELECT IFNULL(SUM(available_stock),0) FROM flash_sale_product.product' }
    [pscustomobject]@{ Name = 'product.fingerprint'; Old = ($productFp -f 'flash_sale');                                       New = ($productFp -f 'flash_sale_product') }
    [pscustomobject]@{ Name = 'activity.rows';       Old = 'SELECT COUNT(*) FROM flash_sale.seckill_activity';                 New = 'SELECT COUNT(*) FROM flash_sale_activity.seckill_activity' }
    [pscustomobject]@{ Name = 'activity.stock';      Old = 'SELECT IFNULL(SUM(seckill_stock),0) FROM flash_sale.seckill_activity'; New = 'SELECT IFNULL(SUM(seckill_stock),0) FROM flash_sale_activity.seckill_activity' }
    [pscustomobject]@{ Name = 'activity.preheat';    Old = ($preheatDist -f 'flash_sale');                                      New = ($preheatDist -f 'flash_sale_activity') }
    [pscustomobject]@{ Name = 'activity.fingerprint'; Old = ($activityFp -f 'flash_sale');                                     New = ($activityFp -f 'flash_sale_activity') }
    # ---- P4 Day 4 新增：order 域（权威 = flash_sale_order）----
    [pscustomobject]@{ Name = 'order.rows';          Old = 'SELECT COUNT(*) FROM flash_sale.seckill_order';                    New = 'SELECT COUNT(*) FROM flash_sale_order.seckill_order' }
    [pscustomobject]@{ Name = 'order.price_sum';     Old = 'SELECT IFNULL(SUM(seckill_price),0) FROM flash_sale.seckill_order'; New = 'SELECT IFNULL(SUM(seckill_price),0) FROM flash_sale_order.seckill_order' }
    [pscustomobject]@{ Name = 'order.tail_fp';       Old = ($orderTailFp -f 'flash_sale');                                     New = ($orderTailFp -f 'flash_sale_order') }
    [pscustomobject]@{ Name = 'msglog.rows';         Old = 'SELECT COUNT(*) FROM flash_sale.seckill_message_log';              New = 'SELECT COUNT(*) FROM flash_sale_order.seckill_message_log' }
    [pscustomobject]@{ Name = 'msglog.status_dist';  Old = ($logStatusDist -f 'flash_sale');                                   New = ($logStatusDist -f 'flash_sale_order') }
    [pscustomobject]@{ Name = 'msglog.tail_fp';      Old = ($logTailFp -f 'flash_sale');                                       New = ($logTailFp -f 'flash_sale_order') }
)

# ---- 4. run reconciliation ----
Write-Output '=== reconcile: flash_sale(副本) vs flash_sale_product / flash_sale_activity / flash_sale_order(权威) ==='
Write-Output ("{0,-22} {1,-36} {2,-36} {3}" -f 'CHECK', 'LEGACY', 'NEW', 'RESULT')

$diffs = @()
foreach ($c in $checks) {
    $old = Invoke-Scalar $c.Old
    $new = Invoke-Scalar $c.New
    $ok = ($old -eq $new)
    if (-not $ok) { $diffs += [pscustomobject]@{ Name = $c.Name; Old = $old; New = $new } }
    Write-Output ("{0,-22} {1,-36} {2,-36} {3}" -f $c.Name, $old, $new, $(if ($ok) { 'OK' } else { 'DIFF <--' }))
}

Write-Output ''
if ($diffs.Count -eq 0) {
    Write-Output ("RESULT: consistent (" + $checks.Count + "/" + $checks.Count + " checks)")
    exit 0
}

Write-Output ("RESULT: " + $diffs.Count + " difference(s) found - dual-write missed writes or mirror failed; fix before switching reads")
$diffs | ForEach-Object { Write-Output ("  ! " + $_.Name + ": legacy=" + $_.Old + "  new=" + $_.New) }
exit 1
