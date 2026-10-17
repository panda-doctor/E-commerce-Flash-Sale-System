# 运维手册（Runbook）

> 适用版本：P5 收官（`tag p5-done`）之后。所有命令默认在仓库根 `E-commerceFlashSaleSystem/` 下执行。
> 目标：任一故障都能在 **5 分钟内定位到层**（边缘 / 交易链路 / 派生数据 / 数据源），并给出**可复制**的处置命令。

---

## 0. 快速定位

### 0.1 服务与端口

| 运行时 | 端口 | 职责 | 是否有自己的库 |
|---|---|---|---|
| `flash-sale-bootstrap`（单体） | **8081** | 健康检查 / 榜单与指标读端点 / `/internal/seckill`（库存） / 静态资源 | **无**（`mysql=ABSENT` 属设计） |
| `flash-sale-auth` | **8082** | 账号 / 会话权威 + `/internal/auth/sessions/validate` | `flash_sale_auth` |
| `flash-sale-ai` | **8083** | AI 客服 | 无 |
| `flash-sale-product` | 见其 `application.yaml` | 商品域 | `flash_sale_product` |
| `flash-sale-activity` | 见其 `application.yaml` | 活动域（预热/重置库存） | `flash_sale_activity` |
| `flash-sale-seckill` | 见其 `application.yaml` | 秒杀执行（Lua + Stream 生产） | 无（Redis + Stream） |
| `flash-sale-order` | **8087** | 消费落单 / 订单读 / 快照写 / 投影回填端点 | `flash_sale_order` |
| `flash-sale-gateway` | **8090** | 唯一公网入口（`/api/**`） | 无 |

> 端口写法以各模块 `src/main/resources/application.yaml` 的 `server.port` 为唯一事实；上表带粗体的均已实测。

### 0.2 三条最快的判活命令

```powershell
# ① 应用层（单体 health：mysql 应为 ABSENT、redis UP、projection 应为 UP）
curl.exe -s http://127.0.0.1:8081/actuator/health

# ② 经网关的业务链路（200 即边缘通）
curl.exe -s -o NUL -w '%{http_code}' 'http://127.0.0.1:8090/api/products?page=1&size=1'

# ③ 一键质量门禁（构建 + 143 回归 + 投影对账）
.\scripts\ci-verify.ps1 -WithDataChecks      # 期望 RESULT: GREEN
```

### 0.3 令牌与身份

| 令牌 | 头 | 取值 | 用于 |
|---|---|---|---|
| 管理令牌 | `X-Admin-Token` | `.env` 的 `ADMIN_TOKEN` | `/api/admin/**` |
| 内部令牌 | `X-Internal-Token` | `${INTERNAL_TOKEN:dev-internal-token}`（`.env` 未覆盖则用默认值） | `/internal/**`（集群内调用） |
| 用户会话 | Cookie + `X-CSRF-Token` | 登录返回的 `csrfToken` | 秒杀/订单等用户态写操作 |

**失败语义速查**：`40100` = 未认证/凭证缺失（网关或服务侧）；`40301` = CSRF 校验失败（缺头或与会话不匹配）；`50000` = 依赖不可用或未捕获异常（看日志里的 `业务异常 | code=50000, message=…`）。

---

## 1. 场景①：消费停摆（订单不再增加）

### 判据

```powershell
# 队列有积压但订单不增；或 PEL 堆积
# （Redis 业务库是 db=1）
# 生产可用 redis-cli；本仓库脚本用 RESP 直连，见 scripts/*.ps1 的 RedisCommand 实现
# 关键观测：XLEN seckill:order:stream、XPENDING seckill:order:group、XLEN seckill:order:dead:stream
```

同时 `/actuator/health` 的 `projection.deadLetterCount > 0` 或指标里的 `queuedMessageCount` 持续上升。

### 处置

```powershell
# 1) 看消费者是否在跑（order 应用日志里应有 consumer group 心跳/消费日志）
# 2) 死信重放（管理端点，幂等）
$tok = ((Get-Content .env -Encoding UTF8 | Where-Object { $_ -match '^ADMIN_TOKEN=' }) -replace '^ADMIN_TOKEN=','').Trim()
curl.exe -s -X POST http://127.0.0.1:8090/api/admin/seckill/dead-letters/replay -H "X-Admin-Token: $tok"
# 3) 若是消息体本身有问题（如坏商品/坏活动），落库失败会进死信 —— 修数据后重放
```

---

## 2. 场景②：投影漂移（指标/榜单与库不一致）

> 读模型是**派生数据**：`seckill:rank:*`（榜单）、`seckill:rank:order:*`（订单号映射）、`seckill:metric:*`（订单数与拒绝计数）。
> 默认 `module.analytics.metrics-source=projection`（读投影）；`=order` 可一行回退到跨服务计数。

### 判据

```powershell
.\scripts\reconcile-analytics-projection.ps1     # 逐活动对比：orderCount / ZCARD / HLEN
# RESULT: consistent → 正常；RESULT: mismatch → 需重建
```

或 `/actuator/health` 出现 `"projection":{"status":"DEGRADED", ...}`（`projectionFailureTotal>0` 或 `deadLetterCount>0`）。

### 处置（三步，全部幂等）

```powershell
# 1) 清理：删掉 metric 与幂等键（注意：脚本必须能把大回复读干净，见第 6 节坑 ③）
.\scripts\purge-analytics-projection.ps1        # 期望 matched ≈ 4028 个 dedupe 键（按你的数据量）

# 2) 重建：走 Java 内部端点（复用 recordSuccess 的序列化器 + 幂等）
$acts = @('90173201268736')                     # 或从库里查 DISTINCT activity_id
foreach ($a in $acts) {
  curl.exe -s -X POST "http://127.0.0.1:8087/internal/orders/activities/$a/rebuild-projection" `
    -H "X-Internal-Token: dev-internal-token"
}

# 3) 对账：必须 0 差异
.\scripts\reconcile-analytics-projection.ps1     # RESULT: consistent / exit 0
```

**绝不要**用脚本直接往 Redis 写投影值 —— 应用用 Jackson 序列化，裸字符串会让读侧 `SerializationException` → 500（坑 ①）。

---

## 3. 场景③：服务不可达 / 被网关拒绝

| 现象 | 定位 | 处置 |
|---|---|---|
| 网关返回 **503** | 目标服务未注册/无实例（日志 `No servers available for service: flash-sale-xxx`） | 起该服务，或先临时把 `module.analytics.metrics-source=order` 切回（若依赖 order 的旧路径） |
| 直连服务 **404**、经网关 200 | 该端点已迁到别的应用（P2~P4 多次搬家） | 查 `docs/plan.md` 的端点归属表 + 网关 `routes` 精确 Path |
| **40100** | 网关准入（无 `X-Admin-Token` / 无会话）或服务侧拦截器 | 带对令牌；用户态检查 Cookie 与 `X-CSRF-Token` |
| **40301** | CSRF：header 与 Cookie 不一致，或与会话里的哈希不匹配 | 重新登录拿新 `csrfToken` |
| `/internal/**` 一律 **40100** | 内部令牌缺失/不匹配（fail-closed） | 带 `X-Internal-Token`；确认各应用 `module.internal.token` 一致 |

---

## 4. 场景④：数据源与缓存异常

| 现象 | 判据 | 说明 |
|---|---|---|
| 单体 `/api/health` 里 `mysql=ABSENT` | 正常 | **P4 收官后单体不连任何库**（健康检查是可选依赖）；不要误判为故障 |
| `actuator.health` 无 `db` 组件 | 正常 | 同上（`spring.autoconfigure.exclude` 排除了数据源自动配置） |
| 某域报 SQL 错误 | 只可能是该域自己的库 | `flash_sale_product / _activity / _order / _auth` 四库互不共享；旧库 `flash_sale` **已 DROP** |
| Redis 报错/超时 | `actuator.health` 的 `redis` 组件 | 秒杀库存、榜单、投影、幂等键全在 Redis（db=1）；Redis 不可用 = 写侧全停、读侧指标不可用 |
| Nacos 注册失败 | 日志 `No instances available` / `9848` 连接失败 | 需映射 **9848**（客户端 gRPC），否则注册不生效 |

---

## 5. 发布前检查单

```powershell
# ① 质量门禁（构建 + 143 回归 + 投影对账）
.\scripts\ci-verify.ps1 -WithDataChecks        # 必须 RESULT: GREEN

# ② 结构守卫（4 条收官不变量：无 @MapperScan / 装配面不含域包 / 数据源排除走属性 / 镜像类已删除）
.\mvnw.cmd -pl flash-sale-bootstrap "-Dtest=PostMigrationInvariantsTest" test

# ③ 健康与漂移
curl.exe -s http://127.0.0.1:8081/actuator/health     # 无 DEGRADED

# ④ 测试库快照与权威库行数对齐（回归前置）
.\scripts\create-test-db.ps1                          # 逐表 ✓

# ⑤ 提交纪律：red 不提交；一次提交只做一件事；改被依赖模块后务必 install
```

---

## 6. 已知坑清单（都是本项目真实踩过的）

| # | 坑 | 表现 | 正确做法 |
|---|---|---|---|
| ① | **脚本直接写 Redis 投影** | 应用读回 `SerializationException` → 500 | 派生数据只走**属主的写入器**（应用内 `RedisTemplate`）；运维脚本只做清理/对账 |
| ② | **catch-all 吞异常** | 榜单正常、计数缺一半，日志无 ERROR | 关键写入要**独立 try/catch + 失败计数**（本项目已有 `projectionFailure`） |
| ③ | **RESP 回复没读干净** | `KEYS` 4028 个键只读到 1285 → 幂等键残留 → 计数"按设计跳过"，看着像业务 bug | 循环读直到无数据（见 `purge-analytics-projection.ps1`） |
| ④ | **旧 jar 误验** | 改了 analytics 却验证不到新逻辑 | `mvnw spring-boot:run -pl <app>` 只编译该 app，其余取 `~/.m2` ⇒ **先 `install`** |
| ⑤ | **PowerShell 两个坑** | ① 无 BOM 的 `.ps1` 里的中文会破坏引号解析；② `rd` 是 `Remove-Item` 别名，会劫持同名函数 | 脚本写**纯 ASCII**；函数名避开内置别名（用 `RedisCall` 之类） |
| ⑥ | **`$ErrorActionPreference='Stop'` + 原生命令** | Maven 写 stderr 的 WARNING 被当成致命错误，脚本中断 | 门禁脚本用**退出码**判定，`Continue` 更安全 |

---

## 8. 混沌演练记录（P6，2026-10-17 首次）

**演练 1：auth 服务缺席时的会话保护路径**（只起单体 8081，auth 8082 / order 8087 均未起）

| 请求 | 实测 | 期望 | 判定 |
|---|---|---|---|
| `GET /api/seckill/users/me/orders`（**无** Cookie） | `40100 请先登录` | 短路、不打下游 | ✅ 符合 |
| 同上（**带** `Cookie: FLASH_SESSION=deadbeef`） | `40100 请先登录` | **拒绝**（fail-closed） | ✅ 拒绝正确，但**错误码无法区分"依赖不可用"** ⚠️ |
| `GET /api/rank/top10?activityId=…`（公开读投影） | `code=0` | 不受 auth/order 影响 | ✅ 符合 |

**结论与待办**
1. **安全语义正确**：下游校验不可用时一律拒绝，绝不放行；
2. **上表第 2 行的 `40100` 是演练脚本缺陷，不是产品缺陷**（2026-10-17 更正）：
   我手写的 `Cookie: FLASH_SESSION=deadbeef` **没有被识别**（cookie 名与实际配置不一致 / 未先登录拿真名），
   于是 `SessionGuard` 在"取不到 token"处就返回 null → 走到 `40100`，**根本没有触发跨进程校验**。
   事后复核代码：`SessionGuard.authenticate` 不吞异常、`ApiAccessInterceptor` 只把 `null → 40100`、
   `HttpSessionValidationAdapter` 对 transport 失败抛 `BusinessException(SYSTEM_ERROR)`（→ `50000`）——
   **三者语义与文档一致**，无需修复。
3. **正确演练步骤（待重跑）**：① 起单体 + auth → ② `POST /api/auth/login` 拿真实 Cookie 名与值 →
   ③ 停 auth → ④ 用该 Cookie 重放用户态请求，**期望 `50000`**（依赖不可用）、且**不得**返回 200。
   教训：**演练必须先拿到真实凭证再制造故障**，否则测到的是"未登录"而不是"依赖不可用"。
4. 演练 2（停 order 看读侧降级）此前已实测通过：`metrics/rank` 在 order 缺席时仍正确返回（见 `plan.md` 的 M6 运行时证据）。

---

## 7. 附录：本次迁移的关键不变量（回归会守住）

1. 单体**没有** `@MapperScan`，装配面不含 `product/activity/order/seckill` 域包；
2. 数据源排除必须走**属性**（`spring.autoconfigure.exclude`），类级 `@SpringBootApplication(exclude=…)` 禁用（测试无法覆盖）；
3. 四个反向镜像类（`DualWriteMirror` / `DualWriteProperties` / `OrderMirror` / `AuthMirror`）必须**不存在**；
4. 用例数下限 **143**（门禁脚本会检查数量，防止"删用例变绿"）。
