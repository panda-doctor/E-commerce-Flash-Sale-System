<#
  P5 Day 3 helper: delete analytics projection keys by pattern (RESP client copied from
  reconcile-analytics-projection.ps1, which is proven to parse replies correctly).

  Used before a clean rebuild: purge orderCount (seckill:metric:*) and the idempotency
  keys (seckill:dedupe:order:*), then rebuild through the Java endpoint so that values
  are written with the application's own serializer.

  Usage:
    .\scripts\purge-analytics-projection.ps1                      # purge metric + dedupe
    .\scripts\purge-analytics-projection.ps1 -Patterns 'seckill:rank:*'
#>
param(
    [string[]]$Patterns = @('seckill:metric:*', 'seckill:dedupe:order:*'),
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6379,
    [int]$RedisDb = 1
)

$ErrorActionPreference = 'Stop'

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
        Start-Sleep -Milliseconds 120
        $buf = New-Object byte[] 65536
        $n = $stream.Read($buf, 0, $buf.Length)
        return [Text.Encoding]::UTF8.GetString($buf, 0, $n)
    } finally {
        $client.Close()
    }
}

function RedisKeys([string]$pattern) {
    # 注意：KEYS 的回复可能远超单次 Read 的缓冲区（实测 4028 个键时只读到 1285 个 →
    # 幂等键残留 → 回填时 SETNX 返回 false、计数被正确跳过，看起来像"写丢了"）。
    # 因此必须**循环读干净**再关闭连接。
    $client = New-Object Net.Sockets.TcpClient($RedisHost, $RedisPort)
    $sb = New-Object Text.StringBuilder
    try {
        $stream = $client.GetStream()
        $payload = '*2' + "`r`n" + (Bulk 'SELECT') + (Bulk "$RedisDb")
        $payload += '*2' + "`r`n" + (Bulk 'KEYS') + (Bulk $pattern)
        $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
        $deadline = (Get-Date).AddSeconds(10)
        $buf = New-Object byte[] 262144
        do {
            Start-Sleep -Milliseconds 60
            while ($stream.DataAvailable) {
                $n = $stream.Read($buf, 0, $buf.Length)
                if ($n -gt 0) { [void]$sb.Append([Text.Encoding]::UTF8.GetString($buf, 0, $n)) }
            }
        } while ($stream.DataAvailable -and ((Get-Date) -lt $deadline))
    } finally {
        $client.Close()
    }
    $keys = New-Object System.Collections.ArrayList
    foreach ($line in ($sb.ToString() -split "`r`n")) {
        $t = $line.Trim()
        if ($t -ne '' -and $t -notmatch '^[*+:-]' -and $t -notmatch '^\$') { [void]$keys.Add($t) }
    }
    return , $keys
}

$total = 0
foreach ($pattern in $Patterns) {
    $keys = RedisKeys $pattern
    if ($keys.Count -eq 0) {
        Write-Output "  pattern=$pattern matched=0"
        continue
    }
    $cmd = @('DEL') + @($keys)
    [void](RedisCommand $cmd)
    $total += $keys.Count
    Write-Output "  pattern=$pattern matched=$($keys.Count) deleted"
}

Write-Output "=== purge done: $total keys deleted ==="
