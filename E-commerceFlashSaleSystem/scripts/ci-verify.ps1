<#
  P6 Day 1: one-command verification gate (local CI).

  Phases:
    1) install        - compile & install all modules (so spring-boot:run and downstream
                        modules always see fresh artifacts; skipping this bit us once:
                        running an app with a stale analytics jar).
    2) clean test     - full regression; PASS requires >= 143 tests with 0 failures / 0 errors.
                        (143 = 136 business cases + 4 post-migration invariant guards + 3 session cache cases)
    3) data checks    - optional (-WithDataChecks): reconcile the analytics projection
                        against flash_sale_order; requires MySQL + Redis + demo data.

  Exit code: 0 = all green, 1 = failure.

  Usage:
    .\scripts\ci-verify.ps1
    .\scripts\ci-verify.ps1 -WithDataChecks
    .\scripts\ci-verify.ps1 -SkipTests          # only build (+ optional data checks)
#>
param(
    [switch]$WithDataChecks,
    [switch]$SkipTests,
    [int]$MinTests = 143
)

# 注意：不能用 'Stop' —— Maven 会把 WARNING 写到 stderr，PowerShell 在 Stop 模式下会把它
# 当成终止性错误（实测直接把脚本打断）。这里统一用退出码（$LASTEXITCODE）判定成功与否。
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$mvn = Join-Path $root 'mvnw.cmd'
if (-not (Test-Path $mvn)) { throw "mvnw.cmd not found at $mvn" }

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\JDK\jdk-21.0.12\jdk-21.0.12.1' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$failures = New-Object System.Collections.ArrayList
function Step([string]$name) { Write-Output ''; Write-Output "=== $name ===" }
function Pass([string]$msg) { Write-Output "  [PASS] $msg" }
function Fail([string]$msg) { Write-Output "  [FAIL] $msg"; [void]$failures.Add($msg) }

# ---------- 1. install ----------
Step '1/3 install (skip tests)'
Push-Location $root
try {
    & $mvn -DskipTests install *> "$env:TEMP\ci-install.log"
    if ($LASTEXITCODE -eq 0) { Pass 'install BUILD SUCCESS' }
    else {
        Fail 'install BUILD FAILURE'
        Select-String -Path "$env:TEMP\ci-install.log" -Pattern 'ERROR.*\.java' | Select-Object -First 5 |
            ForEach-Object { '    ' + $_.Line.Trim() }
    }
} finally { Pop-Location }

# ---------- 2. clean test ----------
if (-not $SkipTests) {
    Step '2/3 clean test (full regression)'
    Push-Location $root
    try {
        & $mvn clean test *> "$env:TEMP\ci-test.log"
        $buildOk = ($LASTEXITCODE -eq 0)
    } finally { Pop-Location }

    $total = 0; $failed = 0; $errors = 0
    Get-ChildItem -Path $root -Recurse -Filter '*.txt' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match 'surefire-reports' } |
        ForEach-Object {
            $hit = Get-Content $_.FullName -TotalCount 6 -ErrorAction SilentlyContinue |
                Select-String -Pattern 'Tests run: (\d+), Failures: (\d+), Errors: (\d+)' | Select-Object -First 1
            if ($hit) {
                $g = $hit.Matches[0].Groups
                $total += [int]$g[1].Value; $failed += [int]$g[2].Value; $errors += [int]$g[3].Value
            }
        }
    Write-Output "  TOTAL=$total failures=$failed errors=$errors (baseline >= $MinTests)"
    if (-not $buildOk) { Fail 'maven reported BUILD FAILURE' }
    if ($failed -gt 0 -or $errors -gt 0) { Fail "test failures=$failed errors=$errors" }
    if ($total -lt $MinTests) { Fail "test count $total < baseline $MinTests (cases deleted or not executed)" }
    if ($failed -eq 0 -and $errors -eq 0 -and $total -ge $MinTests -and $buildOk) {
        Pass "regression green ($total cases)"
    }
} else {
    Step '2/3 clean test (skipped)'
    Write-Output '  [SKIP] -SkipTests'
}

# ---------- 3. data checks ----------
Step '3/3 data checks'
if ($WithDataChecks) {
    $reconcile = Join-Path $PSScriptRoot 'reconcile-analytics-projection.ps1'
    Push-Location $root
    try {
        & $reconcile *> "$env:TEMP\ci-reconcile.log"
        $rc = $LASTEXITCODE
    } finally { Pop-Location }
    Get-Content "$env:TEMP\ci-reconcile.log" -ErrorAction SilentlyContinue | Select-Object -Last 2 |
        ForEach-Object { '    ' + $_.ToString().Trim() }
    if ($rc -eq 0) { Pass 'analytics projection reconcile: consistent' }
    else { Fail "analytics projection reconcile: mismatch (exit=$rc) -> run purge + rebuild" }
} else {
    Write-Output '  [SKIP] data checks (use -WithDataChecks; needs MySQL + Redis + demo data)'
}

# ---------- summary ----------
Write-Output ''
if ($failures.Count -eq 0) {
    Write-Output 'RESULT: GREEN'
    exit 0
}
Write-Output "RESULT: RED ($($failures.Count) failure(s))"
foreach ($f in $failures) { Write-Output "  - $f" }
exit 1
