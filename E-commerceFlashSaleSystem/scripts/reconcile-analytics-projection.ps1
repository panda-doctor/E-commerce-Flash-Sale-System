<#
  P5 Day 3: reconcile the analytics Redis projection against the authoritative DB.

  Checks per activity:
    A. orderCount (seckill:metric:<id>.orderCount)  vs  COUNT(*) of successful orders
    B. rank ZSet cardinality (ZCARD)                vs  COUNT(DISTINCT user_id)
    C. order map cardinality (HLEN)                 vs  COUNT(DISTINCT user_id)

  Exit code: 0 = all consistent, 1 = mismatch (usable as a release gate).

  Usage:
    .\scripts\reconcile-analytics-projection.ps1
    .\scripts\reconcile-analytics-projection.ps1 -ActivityId 90173201268736
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

function RedisCommand([string[]]$command) {
    $client = New-Object Net.Sockets.TcpClient($RedisHost, $RedisPort)
    try {
        $stream = $client.GetStream()
        $payload = '*2' + "`r`n" + (Bulk 'SELECT') + (Bulk "$RedisDb")
        $payload += '*' + $command.Count + "`r`n"
        foreach ($p in $command) { $payload += (Bulk $p) }
        $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
        Start-Sleep -Milliseconds 60
        $buf = New-Object byte[] 65536
        $n = $stream.Read($buf, 0, $buf.Length)
        $reply = [Text.Encoding]::UTF8.GetString($buf, 0, $n)
        $lines = $reply -split "`r`n" | Where-Object { $_ -ne '' }
        return ($lines | Select-Object -Last 1)
    } finally {
        $client.Close()
    }
}

function RedisInt([string[]]$command) {
    $r = RedisCommand $command
    if ($null -eq $r) { return 0 }
    # RESP 回执可能是 ":1000"（整数）或 "$1\r\n1"（批量字符串的末行）；nil "$-1" 视作 0
    $r = $r.Trim().TrimStart(':', '+')
    if ($r -match '^-?\d+$') { return [int]$r }
    return 0
}

Write-Output "=== reconcile analytics projection (redis db=$RedisDb, status=$SuccessStatus) ==="

$where = "status = $SuccessStatus"
if ($ActivityId -gt 0) { $where += " AND activity_id = $ActivityId" }
$rows = MySqlQuery "SELECT CONCAT(activity_id, '|', COUNT(*), '|', COUNT(DISTINCT user_id)) FROM flash_sale_order.seckill_order WHERE $where GROUP BY activity_id ORDER BY activity_id"

$diffs = 0
$checked = 0
Write-Output ("  {0,-22} {1,8} {2,10} {3,12} {4}" -f 'activity', 'db_rows', 'proj_cnt', 'zset/hmap', 'result')
foreach ($r in $rows) {
    $parts = $r.ToString().Trim() -split '\|'
    if ($parts.Count -lt 3) { continue }
    $aid = $parts[0]
    $dbCount = [int]$parts[1]
    $dbUsers = [int]$parts[2]

    $redisCount = RedisInt @('HGET', "seckill:metric:$aid", 'orderCount')
    $zcard = RedisInt @('ZCARD', "seckill:rank:$aid")
    $hlen = RedisInt @('HLEN', "seckill:rank:order:$aid")

    $ok = ($redisCount -eq $dbCount) -and ($zcard -eq $dbUsers) -and ($hlen -eq $dbUsers)
    if (-not $ok) { $diffs++ }
    $checked++
    $flag = if ($ok) { 'OK' } else { 'MISMATCH' }
    Write-Output ("  {0,-22} {1,8} {2,10} {3,12} {4}" -f $aid, $dbCount, $redisCount, "$zcard/$hlen", $flag)
}

Write-Output "=== done: checked $checked activities, mismatches $diffs ==="
if ($diffs -gt 0) {
    Write-Output 'RESULT: mismatch (run rebuild-analytics-projection.ps1 first)'
    exit 1
}
Write-Output 'RESULT: consistent'
exit 0
