<#
  P5 Day 3: rebuild the analytics Redis projection from the authoritative DB.

  Projection keys (same shape as SeckillRankServiceImpl.recordSuccess):
    1) rank ZSet      seckill:rank:<activityId>        member=userId  score=created_at(ms)
    2) order map Hash seckill:rank:order:<activityId>  field=userId   value=orderNo
    3) order count    seckill:metric:<activityId>      field=orderCount

  Why: the projection is derived data. Historical orders are not in it until rebuilt,
  and runtime drift (lost messages) can also be repaired here. Rebuild is idempotent:
  the rank ZSet and map Hash of the activity are deleted first.

  Source of truth: flash_sale_order.seckill_order (order domain owns it since P4).
  Note: ZSet score uses created_at because the original requestTime is not persisted;
  relative order inside one activity is preserved (ordered by created_at).

  Usage:
    .\scripts\rebuild-analytics-projection.ps1
    .\scripts\rebuild-analytics-projection.ps1 -ActivityId 90173201268736
#>
param(
    [long]$ActivityId = 0,
    [int]$SuccessStatus = 1,
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6379,
    [int]$RedisDb = 1
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$mysql = 'D:\MySQL\mysql-8.0.34-winx64\bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "mysql.exe not found: $mysql" }

$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    $line = (Get-Content $envFile -Encoding UTF8 | Where-Object { $_ -match '^MYSQL_PASSWORD=' } | Select-Object -First 1)
    if ($line) { $env:MYSQL_PWD = ($line -replace '^MYSQL_PASSWORD=', '').Trim() }
}

function MySqlQuery([string]$sql) {
    & $mysql '--host=127.0.0.1' '--user=root' '--skip-column-names' "--execute=$sql" 2>&1
}

function Bulk([string]$text) { "`$$($text.Length)`r`n$text`r`n" }

function BuildCommand([string[]]$command) {
    $s = '*' + $command.Count + "`r`n"
    foreach ($p in $command) { $s += (Bulk $p) }
    return $s
}

# Send one pipeline batch; we only need it delivered, not per-command replies.
function SendPipeline([string]$body) {
    $client = New-Object Net.Sockets.TcpClient($RedisHost, $RedisPort)
    try {
        $stream = $client.GetStream()
        $payload = '*2' + "`r`n" + (Bulk 'SELECT') + (Bulk "$RedisDb") + $body
        $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
        Start-Sleep -Milliseconds 80
        $buf = New-Object byte[] 65536
        while ($stream.DataAvailable) { [void]$stream.Read($buf, 0, $buf.Length); Start-Sleep -Milliseconds 20 }
    } finally {
        $client.Close()
    }
}

Write-Output "=== rebuild analytics projection (redis db=$RedisDb, status=$SuccessStatus) ==="

Write-Output '--- seckill_order.status distribution ---'
MySqlQuery "SELECT CONCAT('status=', status, ' rows=', COUNT(*)) FROM flash_sale_order.seckill_order GROUP BY status" |
    ForEach-Object { '  ' + $_.ToString().Trim() }

$where = "status = $SuccessStatus"
if ($ActivityId -gt 0) { $where += " AND activity_id = $ActivityId" }
$rows = MySqlQuery "SELECT CONCAT(activity_id, '|', user_id, '|', order_no, '|', UNIX_TIMESTAMP(created_at) * 1000) FROM flash_sale_order.seckill_order WHERE $where ORDER BY activity_id, created_at"

$valid = @($rows | Where-Object { $_.ToString().Trim().Split('|').Count -ge 4 })
if ($valid.Count -eq 0) {
    Write-Output '  no matching orders, nothing to rebuild'
    exit 0
}

$byActivity = @{}
foreach ($r in $valid) {
    $parts = $r.ToString().Trim() -split '\|'
    $aid = $parts[0]
    if (-not $byActivity.ContainsKey($aid)) { $byActivity[$aid] = New-Object System.Collections.ArrayList }
    [void]$byActivity[$aid].Add(@{ User = $parts[1]; OrderNo = $parts[2]; Score = $parts[3] })
}

$totalActivities = 0
$totalOrders = 0
foreach ($aid in ($byActivity.Keys | Sort-Object)) {
    $items = $byActivity[$aid]
    $rankKey = "seckill:rank:$aid"
    $mapKey = "seckill:rank:order:$aid"
    $metricKey = "seckill:metric:$aid"

    $body = (BuildCommand @('DEL', $rankKey)) + (BuildCommand @('DEL', $mapKey))
    foreach ($it in $items) {
        $body += (BuildCommand @('ZADD', $rankKey, $it.Score, $it.User))
        $body += (BuildCommand @('HSET', $mapKey, $it.User, $it.OrderNo))
    }
    # only overwrite orderCount, keep the three reject counters
    $body += (BuildCommand @('HSET', $metricKey, 'orderCount', "$($items.Count)"))
    SendPipeline $body

    $totalActivities++
    $totalOrders += $items.Count
    Write-Output ("  activity={0,-22} orders={1,-6} -> rank/map/count rebuilt" -f $aid, $items.Count)
}

Write-Output "=== done: $totalActivities activities, $totalOrders orders ==="
