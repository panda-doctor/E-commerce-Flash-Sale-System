# JMeter 全链路压测脚本（第 4 阶段 Day 5）

## 场景设计（`seckill_load.jmx`）

三个串行线程组（`TestPlan.serialize_threadgroups=true` 保证顺序）：

| 组 | 规模 | 目的 |
|----|------|------|
| TG0 预埋成功 | 10 用户 × 1 | 各成功 1 单并保留幂等令牌（用户号 900000+thread） |
| TG1 主压 | 5000 用户 × 1 | 唯一用户（2000000+thread）同时抢购，全部请求落到 `/api/seckill/execute` |
| TG2 重复/限流 | 10 用户 × 6 | 复用 TG0 用户连续 6 次 → 命中重复拒绝(40901) 与限流(42900) |

每个请求均：

- POST `{activityId, userId}`（`Content-Type: application/json`）
- JSON Extractor 取业务码 `code`，JSR223 把非 0 的业务拒绝标记为 `unsuccessful` 并写入 `responseMessage=code=xxx`，方便 JMeter 汇总错误口径

变量通过 `-J` 覆盖：`activityId / host / port / seedBase / mainBase`。

## 运行

```powershell
# 1. 准备活动（库存 = 预期成功数，例如 1000；开始时间在过去）
#    管理端控制台创建并预热即可
# 2. 执行
.\run_load.ps1 -ActivityId 17888xxxx -Threads 5000 -Ramp 20
```

产物在 `out/load_<时间戳>/`：`result.jtl`、`dashboard/`（含吞吐/延迟分布）、`summary.txt`。
压测后的数据校验与报告模板见 `docs/load-test-report.md`。

> 提示：JMeter 5000 线程内存开销大，本机 32G 内存可跑；压测完成后观察自动消费收敛到
> DB 订单数 = 库存，再取后端 `metrics` 接口的三类拒绝计数作为精确口径。
