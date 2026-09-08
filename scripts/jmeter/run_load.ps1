# ============================================================
# 秒杀全链路压测运行脚本（JMeter CLI）
# 用法：
#   .\run_load.ps1 -ActivityId <活动ID> [-Threads 5000] [-Ramp 20]
# 前置：后端已启动且自动消费开启（flash.stream.auto-poll=true）；
#        目标活动已预热、时间窗覆盖当前时刻。
# 产物：本次运行目录 out/load_<时间戳>/ 下
#         - result.jtl       采样明细
#         - dashboard/       JMeter HTML 报告（含吞吐与 p50/p90/p99）
#         - summary.txt      控制台摘要
# ============================================================
param(
  [Parameter(Mandatory = $true)][string]$ActivityId,
  [int]$Threads = 5000,
  [int]$Ramp = 20,
  [string]$HostName = 'localhost',
  [int]$Port = 8081
)

$ErrorActionPreference = 'Stop'
$jmeter = 'D:\JMeter\apache-jmeter-5.6.3\bin\jmeter.bat'
$scriptDir = $PSScriptRoot
$plan = Join-Path $scriptDir 'seckill_load.jmx'

if (-not (Test-Path $jmeter)) { throw "JMeter 不存在：$jmeter" }
if (-not (Test-Path $plan)) { throw "测试计划不存在：$plan" }

$outDir = Join-Path $scriptDir ("out\load_" + (Get-Date -Format 'yyyyMMdd_HHmmss'))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$jtl = Join-Path $outDir 'result.jtl'
$dash = Join-Path $outDir 'dashboard'
$summary = Join-Path $outDir 'summary.txt'

Write-Host "== 压测开始：activityId=$ActivityId threads=$Threads ramp=${Ramp}s =="
& $jmeter -n -t $plan -Jhost=$HostName -Jport=$Port -JactivityId=$ActivityId -l $jtl -e -o $dash 2>&1 | Tee-Object -FilePath $summary

Write-Host "== 完成，产物目录：$outDir =="
Write-Host "摘要：$summary"
