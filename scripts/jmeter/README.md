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

## R3 鉴权后的多用户配套（2026-09-08 起强制）

审计整改 R3 之后，用户类接口强制校验 `X-User-Token`：

- 路径：`POST /api/seckill/execute`、`GET /api/seckill/activities/{id}/check`、`GET /api/seckill/users/{userId}/orders`
- 规则：请求中的 `userId` **必须等于令牌绑定的 uid**（绑定关系在后端 `.env` 的 `USER_TOKENS=token:userId,…` 中声明），否则返回 401「用户访问令牌无效 / 请求用户与访问令牌不匹配」
- 演示账号：`user-a:1001 ~ user-f:1006` 已注册（前端顶栏下拉切换即自动携带匹配令牌，见 `frontend/src/utils/store.js` DEMO_ACCOUNTS）

**对本仓库压测脚本的影响**：`seckill_load.jmx` / `seckill_main.jmx` 目前未配置 `X-User-Token`，且在 R3 之前跑通 —— **R3 后直接重跑会整组 401**。重跑前必须满足「每线程 userId 都注册了同 uid 的令牌」，二选一：

1. **小并发（仅验证防超卖/削峰语义）**：把线程用户收敛到已注册的演示六账号（1001~1006），每线程组需按账号补 `X-User-Token`；但账号少，无法仿真大并发唯一用户。
2. **大并发唯一用户（5000 级，真实压测口径）**：先用脚本为计划用到的每个 userId 生成等量 `token:userId` 映射（示例，PowerShell）：

   ```powershell
   # 生成 1..N 个压测用户（uid 与 jmx 的 seedBase/mainBase 段错开即可）
   $base = 2000000; $rows = 1..5000 | ForEach-Object {
     "jmeter-user$($_):$($base + $_)"
   } -join ','
   # 追加到后端 .env 的 USER_TOKENS（当前已有 user-a:1001~user-f:1006，注意逗号连接）
   Add-Content -Path ../../E-commerceFlashSaleSystem/.env -Value "USER_TOKENS=user-a:1001,...,jmeter-user1:2000001,..." # 示意，实际拼接 $rows
   ```

   再在 JMeter 用 **CSV Dataset** 放 `token,userId` 两列，每线程取一行，Header Manager 加 `X-User-Token=${token}`、body/参数 userId 用同一行的 `userId` —— **禁止单令牌跑多个不同 uid**（会被 401「令牌不匹配」拒绝）。

> 约束说明：R3 后「令牌白名单」是唯一身份来源，5000 并发 = 5000 条注册映射，属鉴权语义与超大并发的固有张力；如需在无鉴权口径下压纯粹并发上限，需另行讨论（如临时候选令牌池 / 压测专用通道），不要试图绕过绑定校验。
