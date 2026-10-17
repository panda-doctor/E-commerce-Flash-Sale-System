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
    $reply = RedisCommand @('KEYS', $pattern)
    $keys = New-Object System.Collections.ArrayList
    foreach ($line in ($reply -split "`r`n")) {
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
