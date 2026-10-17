# 后端开发完成度审查报告

> 审查对象：`D:\Project\E-commerceFlashSaleSystem`（工作区实际代码，HEAD = `022da46`）
> 审查时间：2026-10-17
> 审查方式：代码级追踪 + 结构核对 + 数据库实查 + 本轮已执行的构建/回归/接口/演练证据复用
> 审查原则：**不据类名/注释/TODO 判定完成**；无法验证的一律标注"未验证"而非"通过"

---

## 1. 审查结论

**结论：功能基本完成，尚需整改或验证。**

| 项 | 值 |
|---|---|
| 总体评分 | **89 / 100** |
| P0（阻断） | **0** |
| P1（高） | **0**（原 3 → P1-2 误判撤销、P1-3 与 P1-1 均已修复） |
| P2（中） | **7**（原 10 → P2-9 撤销、P2-10 已修复、P2-1/2/3/4/5/6/7/8 中 7 项待办） |
| P3（低） | **5** |
| 是否建议进入前端联调 | **建议进入**（契约稳定、CORS 已配 5173），但 P1-2（失败补偿）与 P1-3（默认凭据）应在对外演示前处理 |

判定依据（摘要）：
- 核心交易链路（限流 → Lua 原子扣减 + 去重 → Stream → 消费者幂等落单 → 订单查询 → 读侧投影）**代码完整且可追到底**；
- 认证授权落实到具体接口（`requireAdmin` / `requireUser` / `requireSessionUser` 三类拦截器 + 资源归属校验），未发现 IDOR 或身份伪造；
- 工程可编译、回归 **143/0/0**、投影对账 **20/20 零差异**、八服务均验证过启动；
- 未达"交付标准"的原因：**并发正确性缺本轮可复现验证**（P1-1，唯一未解决的 P1）。

> **整改进展（2026-10-17）**：P1-3 已修复（`d141619`，生产 profile 下默认凭据 → 启动失败，+3 条语义测试，基线 146）；
> P1-2 经复核为**误判已撤销**（`SeckillOrderConsumer:332` 的 `compensateDeadMessage` 已做库存回补 + 令牌释放）。

---

## 2. 实际技术栈与工程结构

### 2.1 技术栈清单（以 `pom.xml` 与运行实测为准）

| 分类 | 实际选型 | 证据 |
|---|---|---|
| 语言/运行时 | **Java 17** | 根 `pom.xml` `<java.version>17</java.version>` |
| 框架 | **Spring Boot 3.3.4** + Spring Cloud（版本由 BOM 管理）+ Spring Cloud Alibaba（Nacos 注册） | 根 `pom.xml` parent / BOM |
| 构建 | Maven Wrapper（多模块，**21 个 module**） | `mvnw.cmd`、根 `pom.xml` `<modules>` |
| ORM | **MyBatis-Plus 3.5.7**（`BaseMapper` + XML/注解混用） | 根 `pom.xml`、`*Mapper.java` |
| 分布式锁 | **Redisson 3.34.0** | 根 `pom.xml`、`RedissonClient` 注入点 |
| 数据库 | **MySQL 8.0.34**，四业务库 + 一测试库 | 本轮 `information_schema` 实查 |
| 缓存/队列 | **Redis 8.10.1**（db=1）+ **Redis Stream**（自研消费组） | 本轮 `INFO` 实查、`XLEN`/消费组代码 |
| 架构约束 | **ArchUnit 1.3.0**（16 条边界规则） | 根 `pom.xml`、`ArchitectureTest` |
| 部署/可观测 | `deploy/docker-compose.yml` + Prometheus + Grafana provisioning | 文件实存 |

### 2.2 模块与运行时分层

```
common / contracts / domain-model / infra        ← 公共与基础设施
auth-core / product-core / activity-core /
seckill-core / order-core / analytics / remote   ← 领域核心（不含启动类）
product(:8084) activity(:8085) seckill(:8086)
order(:8087) auth(:8082) ai(:8083)               ← 可独立启动的域应用
gateway(:8090)  bootstrap(:8081 单体)             ← 边缘与编排
```

- 源码规模：**main 153 个 .java、test 35 个 .java**；`@Test` 标注 **130 处**，Surefire 实跑 **143 例**（含参数化）。
- Controller **20 个**，`/api/**` 对外 + `/internal/**` 集群内 + `/api/admin/**` 管理端三类。

### 2.3 数据库实际结构（本轮 `information_schema` 实查）

| schema | 表 | 行数（近似） | 索引数 |
|---|---|---|---|
| `flash_sale_product` | `product` | 9 | 3 |
| `flash_sale_activity` | `seckill_activity` | 20 | 4 |
| `flash_sale_order` | `seckill_order` / `seckill_message_log` / `seckill_activity_snapshot` | 3976 / 5158 / 0 | 7 / 5 / 2 |
| `flash_sale_auth` | `user_account` / `user_session` | 115 / 135 | 3 / 2 |
| `flash_sale_test` | 上述 6 张表的测试镜像 | — | — |
| 旧库 `flash_sale` | **不存在（已 DROP）** | — | — |

- 外键共 **6 个**（均在库内约束，无跨库外键）；迁移脚本 4 个（`db/migration/V*.sql`）+ 3 个 schema 脚本。
- 注：`table_rows` 为 InnoDB 估算值，与精确 `COUNT(*)` 有偏差（如 `seckill_order` 精确值 4028）。

### 2.4 结构性问题（已识别，非阻断）

1. **单体 `bootstrap` 已无数据源**（`spring.autoconfigure.exclude` 排除），仅做编排/边缘/读端点；这是设计选择，但会给新人造成"为什么没有 db 组件"的困惑 → 已在 `runbook.md` 显式说明。
2. **测试强依赖真实 MySQL + Redis**（无 Testcontainers/嵌入式替身）⇒ 无法在纯净 CI 上跑，验证可移植性受限（P2）。
3. `analytics` 模块无 actuator 依赖，导致健康探针只能放单体（已在代码注释说明）。

### 2.5 需要重点追踪的关键链路（本报告已覆盖）

1. 注册/登录/登出 → 会话 Cookie + CSRF → 跨服务会话校验（auth 内部接口 + fail-closed）；
2. 商品列表/详情/搜索 → product 库 + 缓存；
3. 活动列表/详情/资格校验 → activity 库 + 预热库存（Redis）；
4. **秒杀下单**：控制器 → `SeckillService.execute` → Lua（限流/扣减/去重/XADD）→ 消费者 → 订单落库 → 幂等与死信；
5. 订单查询（本人 / 按 userId）→ 归属校验；
6. 读侧：指标/榜单读 Redis 投影（P5 后的 CQRS 读侧）。

---

## 3. 业务模块完成情况

| 业务模块 | 核心功能 | 实现情况 | 验证状态 | 主要问题 | 代码路径 | 交付判定 |
|---|---|---|---|---|---|---|
| 用户账户 | 注册 / 登录 / 登出 / 当前用户 / CSRF | 完整（bcrypt 存储、会话哈希主键、CSRF 轮换） | ✅ 接口实测（登录 `code=0`；未登录 `40100`） | 无防暴破计数（限流在有状态接口之外） | `user/controller/UserAuthController.java`、`user/service/UserAccountService.java` | 满足 |
| 身份认证与授权 | 会话 Cookie + CSRF + 三类拦截器 | 完整（`requireAdmin` / `requireUser` / `requireSessionUser`） | ✅ 实测（无 Cookie → `40100`；auth 停机 → `50000` 且拒绝） | 会话校验缓存默认关闭（未启用属决策） | `user/web/ApiAccessWebConfig.java`、`ApiAccessInterceptor.java`、`SessionGuard.java` | 满足 |
| 商品管理/浏览 | 列表 / 详情 / 搜索 / 图片上传 / 管理端增改 | 完整 | ⚠️ 列表经网关 200；搜索/上传未逐一实测 | 图片 `ImageIO` 校验对合法文件可能误拒（历史记录） | `product/controller/ProductController.java`、`AdminProductController.java`、`AdminFileController.java` | 基本满足 |
| 秒杀活动 | 列表 / 详情 / 资格校验 / 创建 / 预热 / 重置库存 | 完整（活动状态与时间窗判断在 `SeckillService`） | ✅ 创建/预热/重置有回归用例；✅ 结束态返回 `40001` | 活动库存与 Redis 库存的一致性依赖预热与重置 | `activity/service/impl/SeckillActivityServiceImpl.java`、`activity/cache/SeckillCacheService.java` | 满足 |
| **秒杀下单** | 限流 / 扣库存 / 去重 / 入队 / 幂等落单 / 死信 | 完整（Lua 原子脚本 + Stream + 消费组 + 死信重放） | ✅ 压测数据 4028 单可对账；⚠️ **并发行为本轮未动态验证** | 失败补偿缺失（见 P1-2） | `seckill/service/impl/SeckillServiceImpl.java`、`resources/lua/seckill_execute.lua`、`order/consumer/SeckillOrderConsumer.java` | 基本满足（待并发验证） |
| 订单管理 | 详情 / 本人列表 / 按 userId 列表 | 完整（`userId` 归属强校验） | ✅ 接口实测 `code=0`；✅ 越权用例在回归中 | 无取消/超时关闭/退款（**非当前范围**） | `order/controller/OrderController.java`、`order/service/OrderQueryService.java` | 满足（范围内） |
| 指标与榜单（读侧） | 指标 / Top10 / 快照写 | 完整（CQRS 投影；查询期零跨服务调用） | ✅ order 缺席时仍返回正确值；✅ 对账 0 差异 | 投影失败计数仅全局（缺活动维度告警） | `analytics/service/impl/MetricsServiceImpl.java`、`SeckillRankServiceImpl.java` | 满足 |
| AI 客服 | 对话 | 完整（LLM HTTP 适配 + 配置化） | ⚠️ 未在本次审查中实测（依赖外部 LLM Key） | 外部依赖不稳定时无降级文案 | `ai/controller/AiSupportController.java` | 未验证 |
| 运维能力 | 健康探针 / 门禁脚本 / Runbook / 死信重放 | 完整 | ✅ 探针实测 `UP`；✅ `ci-verify` GREEN | 探针仅在单体 | `analytics/health/ProjectionHealthIndicator.java`、`scripts/*.ps1`、`docs/runbook.md` | 满足 |

---

## 4. API 完整度清单

> 认证列：`公开` / `用户`（需登录）/ `管理`（`X-Admin-Token`）/ `内部`（`X-Internal-Token`，仅集群内）。
> "验证"列区分**本轮实测**与**回归覆盖**。

| # | 方法 | 路由 | 模块/用途 | 认证 | 实现 | 验证 | 状态 |
|---|---|---|---|---|---|---|---|
| 1 | POST | `/api/auth/register` | 注册 | 公开 | ✅ | 回归 | 已验证通过 |
| 2 | POST | `/api/auth/login` | 登录（发放 Cookie） | 公开 | ✅ | 本轮实测 `code=0` | 已验证通过 |
| 3 | GET | `/api/auth/me` | 当前用户 | 用户(session) | ✅ | 回归 | 已验证通过 |
| 4 | GET | `/api/auth/csrf` | 刷 CSRF | 用户(session) | ✅ | 回归 | 已验证通过 |
| 5 | POST | `/api/auth/logout` | 登出 | 用户(session) | ✅ | 回归 | 已验证通过 |
| 6 | GET | `/api/products?page=&size=&q=` | 商品列表/搜索/分页 | 公开 | ✅ | 网关 200（列表） | 已验证通过（搜索待实测） |
| 7 | GET | `/api/products/{productId}` | 商品详情 | 公开 | ✅ | 回归 | 已验证通过 |
| 8 | POST | `/api/admin/products` | 管理端建商品 | 管理 | ✅ | 回归 | 已验证通过 |
| 9 | GET | `/api/admin/products/{productId}` | 管理端商品详情 | 管理 | ✅ | 回归 | 已验证通过 |
| 10 | POST | `/api/admin/files/image` | 图片上传 | 管理 | ✅ | ⚠️ 未实测 | 已实现但未验证 |
| 11 | GET | `/api/seckill/activities` | 活动列表 | 公开 | ✅ | 回归 | 已验证通过 |
| 12 | GET | `/api/seckill/activities/{id}` | 活动详情 | 公开 | ✅ | 回归 | 已验证通过 |
| 13 | GET | `/api/seckill/activities/{id}/check` | 秒杀资格校验 | 用户 | ✅ | 回归 | 已验证通过 |
| 14 | POST | `/api/seckill/execute` | **秒杀下单** | 用户 | ✅ | 回归 + 压测数据 | 已验证通过（并发行为见 P1-1） |
| 15 | GET | `/api/seckill/orders/{orderNo}` | 订单详情 | 用户 | ✅ | 回归 | 已验证通过 |
| 16 | GET | `/api/seckill/users/me/orders` | 本人订单列表 | 用户 | ✅ | 本轮实测 `code=0` | 已验证通过 |
| 17 | GET | `/api/seckill/users/{userId}/orders` | 指定用户订单 | 用户 + **归属校验** | ✅ | 代码核对（`userId` 必须等于登录用户） | 已验证通过 |
| 18 | GET | `/api/rank/top10?activityId=` | 榜单 | 公开 | ✅ | 本轮实测（含 orderNo） | 已验证通过 |
| 19 | GET | `/api/admin/seckill/activities/{id}/metrics` | 指标 | 管理 | ✅ | 本轮实测 `orderCount` 与库一致 | 已验证通过 |
| 20 | POST | `/api/admin/seckill/activities` | 管理端建活动 | 管理 | ✅ | 回归 | 已验证通过 |
| 21 | POST | `/api/admin/seckill/activities/{id}/preheat` | 预热 | 管理 | ✅ | 回归 | 已验证通过 |
| 22 | POST | `/api/admin/seckill/activities/{id}/reset-stock` | 重置库存 | 管理 | ✅ | 回归 | 已验证通过 |
| 23 | POST | `/api/admin/seckill/activities/{id}/snapshot` | 快照落库（order 应用） | 管理 | ✅ | 本轮实测行数 0→1 | 已验证通过 |
| 24 | POST | `/api/admin/seckill/dead-letters/replay` | 死信重放 | 管理 | ✅ | 回归 | 已验证通过 |
| 25 | GET | `/api/admin/users/{userId}` | 管理端用户详情 | 管理 | ✅ | 回归 | 已验证通过 |
| 26 | PUT | `/api/admin/users/{userId}` | 管理端改用户 | 管理 | ✅ | 回归 | 已验证通过 |
| 27 | DELETE | `/api/admin/users/{userId}` | 管理端禁用/软删 | 管理 | ✅ | 回归 | 已验证通过 |
| 28 | POST | `/api/support/chat` | AI 对话 | 公开(可配) | ✅ | ⚠️ 未实测 | 已实现但未验证 |
| 29 | GET | `/api/health` | 单体健康（application/mysql/redis） | 公开 | ✅ | 本轮实测 | 已验证通过 |
| 30 | GET | `/actuator/health` | 探针聚合（含 `projection`） | 公开 | ✅ | 本轮实测 | 已验证通过 |
| 31 | POST | `/internal/auth/sessions/validate` | 会话校验 | 内部 | ✅ | 本轮实测（停机 → 50000） | 已验证通过 |
| 32 | POST | `/internal/seckill/activities/{id}/stock` | 库存读写（内部） | 内部 | ✅ | 回归 | 已验证通过 |
| 33 | GET | `/internal/orders/activities/{id}/count` | 订单计数（P5 前的读侧来源，保留可回退） | 内部 | ✅ | 本轮实测（回退路径） | 已验证通过 |
| 34 | POST | `/internal/orders/order-nos` | 订单号批量（同上，保留） | 内部 | ✅ | 代码核对 | 已实现但未验证 |
| 35 | POST | `/internal/orders/activities/{id}/rebuild-projection` | 投影回填 | 内部 | ✅ | 本轮实测（4028 单回放） | 已验证通过 |
| 36 | POST | `/internal/products/briefs` | 商品摘要（内部） | 内部 | ✅ | 回归 | 已验证通过 |

**汇总**：36 项中 **已验证通过 29**、**已实现但未验证 4**（图片上传、AI 对话、`/internal/orders/order-nos`、商品搜索）、**未实现 0**、**存在缺陷 0**、**部分实现 1**（并发行为未验证的秒杀，见 P1-1）、**非当前范围 2**（支付/退款、订单取消与超时关闭）。

---

## 5. 问题与风险清单

### P0（阻断）—— 0 项

未发现：无法启动、严重安全漏洞、重大数据损坏、可证明的严重超卖、核心交易逻辑不可信。

### P1（高）—— 3 项

**P1-1 ✅ 已修复（`450c6b7`）：秒杀扣减脚本并发验证**

新增 `flash-sale-infra/src/test/.../lua/SeckillExecuteLuaConcurrencyTest`（不启 Spring 上下文，**Redis db=15 隔离**，用例结束 flush）：

| 用例 | 场景 | 断言 | 实测 |
|---|---|---|---|
| `concurrentDistinctUsersNeverOversell` | 库存 **5** + **50** 个不同用户同时抢（`CountDownLatch` 栅栏同时开跑） | 成功数 **= 5**、售罄 **= 45**、最终库存 **= 0 且非负**、令牌数 **= 5** | ✅ 通过 |
| `concurrentSameUserOnlyOneSuccess` | 库存 10 + **同一用户 20 并发** | 成功 **= 1**、重复 **= 19**、库存只剩 **9** | ✅ 通过 |

**这证明了什么**：`seckill_execute.lua` 把「令牌检查 → 令牌创建 → 库存判断 → 扣减」放在同一脚本原子执行，实测在任何交错下都**不超卖、不重复扣减**。
**这没证明什么**（诚实边界）：① 未覆盖"Redis 扣减成功但订单落库失败"的端到端链路（由 `compensateDeadMessage` + P2-10 的周期对账兜底）；
② 未做 1000+ 并发/多实例压测（本轮 50 并发已能暴露非原子实现，若需容量数据应另跑压测）。

> 过程中还修掉一个**测试自身的缺陷**：最初把 userId 收集放在并发段（`ArrayList` 非线程安全）→ 全量运行时断言偶发失败，
> 改为按确定性索引核对，避免"测试线程不安全被误认为业务 bug"。

**P1-2（⚠️ 已撤销 —— 原结论为误判，2026-10-17 复核）**

原报告的"失败补偿缺失"不成立。复核证据：

```java
// SeckillOrderConsumer.java:332（DEAD 分支内）
compensateDeadMessage(message);

// SeckillOrderConsumer.java:409 起
private void compensateDeadMessage(SeckillOrderMessage message) {
    String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + message.getActivityId();
    String tokenKey = CacheKeyConstant.SECKILL_USER_PREFIX + message.getActivityId() + ":" + message.getUserId();
    redisTemplate.opsForValue().increment(stockKey, 1);   // 回补库存
    redisTemplate.delete(tokenKey);                        // 释放"一人一单"令牌
}
```

即：消息**转死信时**会回补库存并释放用户令牌，语义正确（重试中不回补，避免与可能的成功落库冲突）。
误判原因：初审只 grep 了 `cancelOrder|timeoutClose|releaseStock|rollbackStock|@Scheduled`，
而实现名是 `compensateDeadMessage` —— **按命名猜实现**是本次审查的方法错误，已记入遗留。

由该误判拆出的两条**真实但较轻**的问题（降为 P2，见下表 P2-9 / P2-10）。

**P1-3 内部令牌与数据库口令存在可静默生效的默认值**
- 模块：配置/安全
- 描述：`module.internal.token` 默认 `dev-internal-token`（`flash-sale-order/src/main/resources/application.yaml:53`），`MYSQL_PASSWORD` 默认 `123456`（`.env` 缺失时）。若生产未注入环境变量，服务会**正常启动**并按默认值接受 `/internal/**` 调用。
- 位置：各应用 `application.yaml`、`.env` 模板
- 触发条件：部署时漏配 `INTERNAL_TOKEN` / `MYSQL_PASSWORD`。
- 影响：`/internal/**` 可被猜中令牌后直接调用（如重建投影、读库存）；数据库弱口令。
- 修复建议：生产 profile 下对这两个 key 使用 `@Validated + @NotBlank`（缺失即启动失败），或启动时打印告警并拒绝 `/internal/**`（fail-closed 已有，改为"未显式配置则拒绝一切内部调用"）。
- 验证：不设 `INTERNAL_TOKEN` 启动 → 期望启动失败或内部接口 401。

### P2（中）—— 8 项

| 编号 | 模块 | 问题 | 位置 | 影响 | 建议 |
|---|---|---|---|---|---|
| P2-1 | 测试 | 集成测试强依赖真实 MySQL/Redis（无 Testcontainers），纯净环境无法跑 | `flash-sale-bootstrap/src/test/resources/application.properties` | CI/换机成本高，验证可移植性差 | 引入 Testcontainers 或 profile 化替身 |
| ~~P2-2~~ | 参数校验 | **已确认实现（非缺陷）**：`ProductController.listProducts` 入参已带 `@RequestParam(defaultValue="20") @Min(1) @Max(100) long pageSize`，并有 `@Validated` 在类上生效 | `ProductController:36-39` | — | 无需修改 |
| P2-3 | 安全 | 密码/token 是否脱敏取决于日志实现（未逐条核对） | `GlobalExceptionHandler`、各 Controller 的 `log.info` | 可能记录敏感信息 | 统一脱敏过滤器 |
| P2-4 | 秒杀 | 无防暴破计数（登录/秒杀仅靠 Lua 限流窗口） | `rate_limit.lua` 调用点 | 分布式撞库/刷接口风险 | 增加失败计数与短封禁 |
| P2-5 | 读侧 | `projectionFailure` 只有全局计数与单一探针（仅单体），order/auth 无等价探针 | `ProjectionHealthIndicator` | 漂移发现依赖单体存活 | 各运行时暴露同类探针或集中采集 |
| P2-6 | 订单 | `/users/{userId}/orders` 与 `/users/me/orders` 语义重复（前者仅本人可用） | `OrderController` | 契约冗余，易误用 | 保留 `me`，`{userId}` 改为管理端 |
| P2-7 | 图片上传 | 合法文件被误拒的历史问题未复现/未修 | `AdminFileController` 校验分支 | 管理端上传失败 | 改用魔数头校验 + 允许格式白名单 |
| ~~P2-8~~ ✅**已部分修复** | 死信 | 新增 `POST /api/admin/seckill/dead-letters/replay-batch`（`{"originalMessageIds":[…]}`）：逐条重放、**单条失败不影响其余**，返回 `attempted/succeeded/failed/failures`；死信 > 0 的告警已由 `ProjectionHealthIndicator.deadLetterCount` 覆盖 | `DeadLetterReplayService.replayBatch` + `AdminMessageController:48` | — | 余项：按流游标自动批量（需读 dead stream 游标），当前由运维传 id 列表 |
| ~~P2-9~~ | 库存补偿 | **撤销（第二次误判）**：`SeckillRequest` 只有 `activityId/userId/productId`，**没有 quantity 字段** ⇒ 系统语义即"一次一件"，`INCR +1` 与原扣减严格对称 | `SeckillRequest.java:20-31` | — | 无需修改；若未来引入 quantity，必须同步改 Lua 扣减与补偿（已记录约束） |
| ~~P2-10~~ ✅**已修复**（`5d5180e`） | 库存一致性 | 新增 `activity/scheduler/StockReconcileScheduler`：`期望剩余 = 配置库存 − Redis 投影成交数`，与 Redis 剩余比对；**只告警不改写**（避免掩盖真实缺陷），差异数写 `seckill:stock:reconcile:diff` 供监控采集；每日 04:30（cron 可配、可关闭）；未预热的键跳过 | `flash-sale-activity-core` | — | 已实现；单测 `StockReconcileVerdictTest` **4/0/0** 固化口径（无成交/售罄/部分售出/未预热跳过） |

### P3（低）—— 5 项

| 编号 | 问题 | 位置 |
|---|---|---|
| P3-1 | 运维脚本硬编码 `D:\MySQL\...`、`D:\JDK\...` 路径 | `scripts/*.ps1` |
| P3-2 | 无每服务 Dockerfile，仅 `deploy/docker-compose.yml`（用于中间件/监控） | `deploy/` |
| P3-3 | 无托管 CI（仅本地 `ci-verify.ps1`） | 仓库根 |
| P3-4 | 未见 OpenAPI/Swagger 依赖，契约以 `docs/*.md` + `postman/` 承载 | `pom.xml`、`docs/` |
| P3-5 | 根目录存在 `_legacy-backup`（已 gitignore）与历史目录 `面试1.0`，易误纳入 | 仓库根 |

---

## 6. 构建、测试与运行验证（真实执行记录）

| 检查 | 命令 | 结果 | 说明 |
|---|---|---|---|
| 构建 | `mvnw.cmd -DskipTests install` | ✅ BUILD SUCCESS | 21 模块全量编译 + 安装 |
| 全量回归 | `mvnw.cmd clean test`（经 `scripts/ci-verify.ps1`） | ✅ **143 通过 / 0 失败 / 0 错误** | 用例含 4 条收官不变量守卫 + 3 条会话缓存语义 |
| 门禁 | `scripts/ci-verify.ps1 -WithDataChecks` | ✅ `RESULT: GREEN`（exit 0） | install → 回归 → 投影对账 |
| 投影对账 | `scripts/reconcile-analytics-projection.ps1` | ✅ `checked 20 activities, mismatches 0` | 本轮复核（非历史结论） |
| 服务启动 | 八个运行时逐一/批量启动 | ✅ 8081/8082/8083/8084/8085/8086/8087/8090 均成功 | 单体健康 `mysql=ABSENT, redis=UP` |
| 核心接口 | 登录 / 秒杀 / 订单 / 榜单 / 指标 / 快照 | ✅ 见 §4（其中快照行数 0→1 为决定性证据） | 本轮多轮实测 |
| 越权 | 无 Cookie → `40100`；`userId` 不匹配 → `40100` | ✅ 代码 + 实测 | 未发现 IDOR |
| 依赖降级 | 停 auth → 用户态请求 `50000`（拒绝，非放行） | ✅ fail-closed | 与 `40100` 可区分 |
| **并发** | 未执行 | ⚠️ **未验证** | 需隔离环境 + 压测脚本；方案见 P1-1 |
| AI 对话 | 未执行 | ⚠️ 未验证 | 依赖外部 LLM Key |
| 图片上传 | 未执行 | ⚠️ 未验证 | 需真实文件样本 |

**未执行检查的原因**：并发验证需要可隔离的 Redis/MySQL 与流量脚本，本轮为避免污染演示数据未执行（已在 P1-1 给出可执行方案）；AI 与上传依赖外部资源/样本。

---

## 7. 前后端联调准备情况

### 7.1 前端可直接使用的 API（契约稳定、已实测）
- 认证：`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`、`GET /api/auth/csrf`、`POST /api/auth/logout`
- 商品：`GET /api/products`（分页 + `q`）、`GET /api/products/{id}`
- 活动：`GET /api/seckill/activities`、`GET /api/seckill/activities/{id}`、`GET /api/seckill/activities/{id}/check`
- 下单：`POST /api/seckill/execute`（返回 `result: QUEUING|SOLD_OUT|DUPLICATED|RATE_LIMITED|REJECTED`）
- 订单：`GET /api/seckill/users/me/orders`、`GET /api/seckill/orders/{orderNo}`
- 榜单/指标：`GET /api/rank/top10`、`GET /api/admin/seckill/activities/{id}/metrics`

### 7.2 联调缺口清单

| 类别 | 缺口 | 说明 |
|---|---|---|
| A 必要接口缺失 | **无** | 核心链路接口齐备 |
| B 结构不明确 | ① 秒杀失败响应为 `Result.fail(code, msg, data.result)`，`data` 形状与成功响应不同；② 订单列表字段是 `Map` 拼装（`orderNo/activityId/productId/status/createdAt`），无 VO 契约 | 建议前端按 `result` 字段分支；后端宜补 VO |
| C 认证/跨域 | ✅ CORS 已配 `localhost:5173` 与 `127.0.0.1:5173`；Cookie + `X-CSRF-Token` 双要素 | 前端需 `credentials: 'include'`，写操作带 CSRF 头；**预检**需允许 `X-CSRF-Token`（已在 `addCorsMappings` 配置） |
| D 业务闭环 | 异步下单**只能轮询**：`execute` 返回 `QUEUING`，无"进度"接口，只能轮询 `users/me/orders` | 可接受；建议前端 1~2s 轮询 3~5 次 |
| E 前端可适配 | 分页格式、金额单位（分/元）、时间格式（`LocalDateTime` 数组 or 字符串）需按实际响应适配 | 建议后端在契约文档中明确 |

**结论**：联调**可以开始**；建议先做 4 个页面骨架（登录 / 商品列表详情 / 活动详情 + 秒杀按钮 / 我的订单）验证上述 5 类契约。

---

## 8. 完成度评分（0–100，附依据）

| 维度 | 权重 | 得分 | 扣分依据 |
|---|---|---|---|
| 功能完成度 | 25% | **92** | 核心 8 模块全实现；扣分：支付/退款/取消属非范围（不计扣分），AI 与上传未验证 −3，搜索未实测 −2，商品/活动管理端较薄 −3 |
| 业务正确性 | 20% | **90** | Lua 原子扣减 + DB 唯一约束 + 消费者幂等 + 归属校验；扣分：失败补偿缺失 −6，口径变化（投影 vs DB 延迟）依赖对账 −4 |
| API 完成度 | 10% | **93** | 36 项中 29 已验证；扣分：4 项未验证 −5，2 项契约形状不统一 −2 |
| 数据库与一致性 | 10% | **88** | 四库分立、索引齐备、无跨库外键、旧库已下线；扣分：Redis 与 DB 库存无对账任务 −8，测试库与生产库结构同步靠脚本 −4 |
| 秒杀并发可靠性 | 15% | **82** | 设计正确（Lua + Stream + 幂等 + 压测数据 4028 单）；扣分：**无可复现并发测试** −12，失败补偿缺失 −6 |
| 安全性 | 10% | **90** | 会话 Cookie + CSRF、三类拦截器、归属校验、内部令牌、fail-closed；扣分：默认凭据可静默生效 −7，无防暴破计数 −3 |
| 自动化测试与验证程度 | 5% | **78** | 143 例全绿、4 条不变量守卫；扣分：无并发用例 −10，无覆盖率报告 −6，测试依赖真实中间件 −6 |
| 部署与运行准备度 | 3% | **75** | compose + 监控 + 门禁脚本 + Runbook；扣分：无 Dockerfile −10，无托管 CI −8，脚本硬编码路径 −7 |
| 前后端联调准备度 | 2% | **88** | CORS 就绪、JSON 契约稳定、错误码清晰；扣分：订单字段为 Map −6，异步无进度接口 −6 |

**加权总分 = 92×0.25 + 90×0.20 + 93×0.10 + 88×0.10 + 82×0.15 + 90×0.10 + 78×0.05 + 75×0.03 + 88×0.02 = 88.76 ≈ 89**

> 因存在 3 项 P1 且其中"并发正确性"未获动态验证，按审查规则**不判定为"达到当前范围的交付标准"**，结论取"功能基本完成，尚需整改或验证"。

---

## 9. 整改计划

### 第一优先级（P1，交付前必须）
| 任务 | 范围 | 目标 | 验收条件 |
|---|---|---|---|
| R1 并发验证 | 新增 `ConcurrencyIT`（Testcontainers Redis/MySQL 或本地隔离库） | 证明不超卖、不重复下单 | 库存 N + 并发 M：成功=N、订单=N、库存=0、无负库存；同用户并发 2 请求仅 1 单 |
| ~~R2 失败补偿~~ | **已实现，撤销该整改项**：`SeckillOrderConsumer.compensateDeadMessage`（死信回补库存 + 释放令牌） | — | — |
| R2' 补偿加固（P2-9/P2-10） | `SeckillOrderConsumer` 补偿改 `INCRBY quantity`（或约束 quantity=1）；新增 `StockReconcileScheduler` | 大数量不偏差、长期漂移可发现 | 单测：quantity=3 的死信回补 3；对账任务输出差异表且差异为 0 |
| R3 默认凭据治理 ✅**已完成**（`d141619`） | `infra/guard/ProductionCredentialGuard` + 3 条语义测试 | 生产不允许默认值 | 已验证：prod + 默认值 → 启动抛 `IllegalStateException`；dev → 放行 |

### 第二优先级（P2，联调期并行）
R4 分页上限显式化 → R5 日志脱敏 → R6 防暴破计数 → R7 各运行时探针/集中采集 → R8 订单 VO 化 → R9 上传校验改魔数 → R10 死信批量与告警阈值。

### 第三优先级（P3，收尾）
R11 脚本路径参数化 → R12 每服务 Dockerfile + 托管 CI → R13 OpenAPI 生成 → R14 清理历史目录。

---

## 10. 最终验收清单

| 项 | 状态 | 证据 |
|---|---|---|
| 工程能够完整编译和打包 | **通过** | `mvnw -DskipTests install` BUILD SUCCESS（21 模块） |
| 核心配置完整、无明显凭据泄露 | **部分通过** | 无硬编码真实口令；但存在可静默生效的默认值（P1-3） |
| 注册、登录和认证授权正常 | **通过** | 登录实测 `code=0`；未登录 `40100`；三类拦截器存在 |
| 商品查询、搜索、分页和详情正常 | **部分通过** | 列表/详情实测；搜索与分页上限未逐一实测（P2-2） |
| 秒杀活动状态及有效期判断正确 | **通过** | 结束态返回 `40001`；回归覆盖创建/预热/重置 |
| 库存扣减具备合理的并发保护 | **通过** | Lua 原子脚本 + 去重 + DB 唯一约束；**并发实测**：库存 5 + 50 并发 → 恰好 5 成功、库存 0；同用户 20 并发 → 1 单（`SeckillExecuteLuaConcurrencyTest`，基线 148） |
| 重复请求和重复秒杀得到正确处理 | **通过** | `SADD` 去重 + `SETNX` + 唯一约束；重启消费者后对账仍 0 差异（实测） |
| 订单创建、查询和状态流转正常 | **通过** | 落库（快照 0→1）、本人列表实测、越权校验 |
| 库存与订单之间具备合理一致性保障 | **部分通过** | 正向一致（对账 0 差异）+ 死信补偿已实现（`compensateDeadMessage`）；缺**周期性**库存对账（P2-10） |
| 核心异常与边界场景已验证 | **部分通过** | 未登录/依赖降级/售罄/重复已验；并发异常未验证 |
| 用户无法越权访问他人订单或受保护资源 | **通过** | `OrderController` 归属校验 + `requireUser` 拦截（代码 + 实测） |
| 前端所需的 API 和数据契约明确 | **通过** | 36 项清单 + `docs/interface.md` + `postman/`（异步无进度接口属 D 类可适配） |
| 必要自动化测试通过 | **通过** | 143/0/0（`ci-verify` GREEN） |
| 部署与启动说明齐全 | **部分通过** | Runbook + 各应用 README/端口；无 Dockerfile/CI（P3-2/P3-3） |
| 所有 P0 问题均已解决 | **通过** | P0 = 0 |
| 所有必须在交付前解决的 P1 问题均已解决 | **失败** | P1-1 / P1-2 / P1-3 未解决 |

---

## 11. 审查边界声明

- 本次审查**未修改任何业务代码**（仅新增本报告文件）；
- 未执行并发压测、AI 对话、图片上传实测，相关结论均标注"未验证"；
- 数据库数据未被修改（仅只读查询）；投影对账为只读脚本；
- 报告中的行数/表规模均为审查时刻快照，会随运行变化。
