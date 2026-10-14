# 微服务架构演进开发计划（第 5 阶段）

> **文档定位**：承接 `docs/plan.md` 第 1~4 阶段（单体架构已全部完成、全量 92 用例 BUILD SUCCESS），规划从单体到微服务的渐进演进路径。
>
> **当前状态**：规划中（未开工）。第九节为 **P0 详细任务卡**，可直接按逐日步骤执行。
>
> **关联文档**：`docs/plan.md`（进度追踪）· `docs/interface.md`（接口契约）· `docs/database.md`（库表）· `docs/load-test-report.md`（压测基线）· `docs/redis-summary.md`（Redis 模式索引）· `docs/storage.md`（图片存储）· `docs/audit-report.md` / `docs/final-review.md`（审计与整改）

---

## 一、现状基线与总原则

### 1.1 单体资产盘点（截至 2026-10-12）

| 维度 | 现状 |
|---|---|
| 架构 | 单 Maven 工程、单进程（:8081）、单库 `flash_sale` + Redis db1 |
| 包结构 | `common/(9) · config/(11) · controller/(13) · domain/(34) · mapper/(7) · service/(24) · stream/(3)`，另有 `task/`、`service/order/`、`service/stream/`、`controller/order/`、`service/ratelimit/` 等空目录残留 |
| 数据 | 7 张表（product / seckill_activity / seckill_order / seckill_message_log / seckill_activity_snapshot / user_account / user_session），7 个实体 |
| 接口 | 20+ REST 接口（商品 / 活动 / 秒杀 / 订单 / 榜单 / 指标 / 用户体系 / AI 客服 / 文件上传 / 死信回放） |
| 已验证能力 | Lua 原子扣减（无超卖 200 并发实证）· 滑动窗口限流 · Redis Stream 削峰 + 消费组落单 · PEL 重试 / 死信 / 人工回放 · Redisson 锁（预热/重置）· 缓存三防护 · 榜单 ZSet NX 幂等 · 指标埋点与快照 |
| 质量基线 | `mvn test` 92 用例 BUILD SUCCESS；JMeter 5000 并发 0 错误、库存归零无超卖；前端 Vue3 + TS 商城/后台双形态 |
| 可观测现状 | 统一 `Result` 信封带 `requestId`；日志按包名分级；无 traceId 跨服务、无指标采集端点 |

### 1.2 总原则

1. **不推倒重来**：以「绞杀者模式」渐进抽取，每步单体均可运行、可回退。
2. **复用已验证资产**：Lua 脚本、Stream/PEL/死信、补偿与对账、JMeter 脚本原样继承，只换部署单元。
3. **边界先于进程**：先用 Maven 多模块 + 包边界 + ArchUnit 把"接口"落在编译期，再拆进程；**禁止跳过模块化单体直接拆服务**。
4. **服务数量有界**：8 服务 + 1 网关封顶（单人可运维上限），不为拆而拆。
5. **可停可回退**：P0~P6 每阶段都有独立验收与回退点，任意阶段停下都是完整可演示系统。

### 1.3 前置条件（开工前必须先做）

| # | 事项 | 说明 |
|---|---|---|
| 1 | **提交当前单体基线** | 工作区尚有未提交内容（用户账号体系 `UserAccount/UserSession`、前端 TS 新架构、面试文档等）；微服务改造从干净 HEAD 开始 |
| 2 | 打基线标签 | `git tag mono-baseline-<date>`，作为 P0 回退锚点 |
| 3 | 契约冻结 | `/api/**` 路径前缀与 `Result{code,message,data,requestId,timestamp}` 信封不改，前端仅换 `VITE_API_BASE_URL` |
| 4 | 全量回归确认 | 停 dev 后端后 `mvn test` 92 用例 BUILD SUCCESS（项目既定纪律） |

---

## 二、架构迁移策略（对应需求 1）

### 2.1 拆分原则

| 原则 | 判定标准 | 本项目落地 |
|---|---|---|
| 业务能力 / 限界上下文优先 | 按现有域包切分，不重新发明边界 | `user / product / activity / seckill / order / analytics / ai` |
| 数据所有权单一 | 每张表、每类 Redis 键只有一个可写服务 | 见 3.4 数据所有权速查 |
| 负载与变更特征分离 | 高并发/高频迭代域独立扩缩容 | 秒杀链（高并发）与商品/活动管理（低频）分离 |
| 有界拆分 | 拆的理由是"独立扩容或独立故障域" | 8 服务封顶，`analytics` 可先合后拆 |
| 可回滚 | 流量可切回旧路径 | 网关权重切换 / 路由回指单体 |

**明确不拆的点**：`限流 → Lua 原子（令牌+扣减+回滚）→ Stream 投递` 必须在一个服务内（一次 Redis 原子编排）；`seckill:stock` 与 `seckill:user` 令牌同属 seckill-service；`uk_order_no`/`uk_activity_user` 唯一键幂等兜底随表迁移。

### 2.2 迁移路径（4 步）

**Step 1｜模块化单体（P0，本阶段不拆进程）**

```
flash-sale-parent
├─ flash-sale-common         零框架依赖（Result/异常/工具/常量）
├─ flash-sale-contracts      跨域契约（消息/事件/DTO）
├─ flash-sale-domain-model   实体 + 枚举（过渡共享，P3/P4 后各域私有化）
├─ flash-sale-infra          Redis/Redisson/MyBatis-Plus/Web 通用配置
├─ flash-sale-user           用户与会话
├─ flash-sale-product        商品 + 缓存三防护 + 图片存储
├─ flash-sale-activity       活动管理 + 预热（含预热锁）
├─ flash-sale-seckill        秒杀执行（限流 + Lua + Stream 生产）
├─ flash-sale-order          消费落单 + 消息日志 + 死信/回放
├─ flash-sale-analytics      榜单 + 指标 + 快照
├─ flash-sale-ai             AI 客服
└─ flash-sale-bootstrap      启动器 + 配置 + 跨域测试 + 过渡装配（对外仍是单进程 :8081）
```

价值：用 **编译期依赖约束 + ArchUnit 规则**替代口头约定；单体一次部署，风险最低。

**Step 2｜边缘服务先拆（P2）**：`auth-service`、`ai-service`（无状态、无跨域事务）→ 验证 Nacos/Gateway/配置中心/追踪全链路基建。

**Step 3｜核心域逐个抽取（P3~P4）**：`product` → `activity` → `seckill` → `order`，每次只拆一个，拆后跑全量回归 + JMeter 复测。

**Step 4｜读模型分离（P5）**：`analytics` 改为事件驱动读模型（订阅 `order.created`），完成 CQRS 收尾。

### 2.3 数据迁移策略

| 步骤 | 做法 | 回滚点 |
|---|---|---|
| Schema 逻辑隔离 | 同实例内按服务分 schema：`db_user / db_product / db_activity / db_order / db_analytics` | 改连接串即回 |
| 双写过渡 | 新服务上线后"旧表 + 新表"双写（或事件回放建新表），读路径影子比对 | 关闭新服务开关 |
| 对账与切读 | 行数 / 金额 / 唯一键对账脚本；一致后切读 → 停双写 → 下线旧表 | 反向同步脚本 |
| 约束随迁 | 全部 UNIQUE KEY 与必要外键随表迁移，幂等语义不降级 | — |

### 2.4 前端迁移策略

- 路由前缀与信封不变，前端**只改 `VITE_API_BASE_URL` 指向网关**；
- 鉴权从"前端携带 `X-Admin-Token` / `X-User-Token`"逐步收敛为"网关统一校验 + 会话 Cookie / JWT"；
- 联调顺序跟随服务抽取：每拆一个服务，用现有前端页面回归该域（P2 起）。
- 详细前端优化项（结构与 UI）另见前端优化方案（`frontend/OPTIMIZATION.md` 及后续 `docs/` 文档）。

---

## 三、服务划分与定义（对应需求 2）

### 3.1 服务清单（8 服务 + 1 网关）

| 服务 | 职责 | 现有代码映射 | 对外接口 | 数据所有权（写） |
|---|---|---|---|---|
| **gateway** | 路由、统一鉴权、限流、CORS、traceId 注入 | 新增（承接 `ApiAccessInterceptor` 跨切面职责） | 全部 `/api/**` 入口 | 无 |
| **auth-service** | 注册/登录/会话/CSRF/令牌 | `controller/auth`、`controller/admin/AdminUserController`、`service/user`、`UserAccount/UserSession`、`PasswordEncoderConfig`、`UserTokenRegistry` | `/api/auth/**`、`/api/admin/users/**` | `user_account`、`user_session`、会话存储 |
| **product-service** | 商品 CRUD、缓存旁路 + 三防护、图片存储 | `controller/product`、`controller/admin/AdminProduct*`、`service/product`、`service/cache/ProductCacheService`、`service/storage`、`FileStorageWebConfig` | `/api/products/**`、`/api/admin/products/**`、`/api/admin/files/**` | `product`、`product:detail:*`、上传文件（本地/OSS） |
| **activity-service** | 活动创建/更新、时间窗、预热编排与状态 | `controller/admin/AdminSeckill*`、`service/seckill/SeckillActivityService`、`service/impl/SeckillActivityServiceImpl`、`service/cache/SeckillCacheService` | `/api/seckill/activities/**`（查询）、`/api/admin/seckill/activities/**` | `seckill_activity`、`seckill:activity:*`、`seckill:lock:preheat/reset:*` |
| **seckill-service** | 限流 → Lua 原子 → Stream 投递；执行侧活动校验 | `controller/seckill/{SeckillExecuteController, SeckillActivityController}`、`service/seckill/SeckillService`、`service/impl/SeckillServiceImpl`、`stream/producer`、`lua/*` | `/api/seckill/execute`、`/api/seckill/activities/{id}/check` | `seckill:stock:*`、`seckill:user:*`、`rate:limit:*`、`seckill:order:stream`（生产端） |
| **order-service** | Stream 消费落单、订单查询、消息日志、重试/死信/回放、补偿 | `stream/consumer`、`stream/scheduler`、`controller/seckill/OrderController`、`service/seckill/DeadLetterReplayService` | `/api/seckill/orders/**`、`/api/seckill/users/**/orders`、`/api/admin/seckill/dead-letters/replay` | `seckill_order`、`seckill_message_log`、`seckill:order:dead:stream` |
| **analytics-service** | 榜单 TopN、运行指标、快照（读模型） | `controller/rank`、`controller/admin/AdminMetricsController`、`service/rank`、`service/metrics`、`service/impl/{SeckillRankServiceImpl, MetricsServiceImpl}` | `/api/rank/**`、`/api/admin/seckill/activities/{id}/metrics|snapshot` | `seckill:rank:*`、`seckill:metric:*`、`seckill_activity_snapshot` |
| **ai-service** | AI 客服（无状态，目录经 product/activity 接口获取） | `controller/support`、`service/ai`、`service/impl/AiChatServiceImpl`、`AiLlmProperties` | `/api/support/chat` | 无库 |

### 3.2 边界关键决策（需拍板并写入契约）

1. **库存归 seckill-service**：activity-service 负责预热编排，但 `seckill:stock:*` 的初始化/重置通过 seckill-service 暴露的内部接口完成（谁的数据谁写）。
2. **`check` 接口归 seckill-service**：其判定口径必须与 `execute` 同源（CANCELLED → 实时时间窗 → 实时库存 → 未预热），不能放 activity-service。
3. **榜单/指标从 order 剥离**：order-service 落库后发布 `order.created` 事件，analytics-service 消费上榜（沿用 `ZADD NX` 幂等）；接受秒级读模型延迟。
4. **死信回放归 order-service**：回放需要"重新原子占位（扣库存+建令牌）"，与 seckill 的数据有交叉——通过 seckill-service 内部接口或契约事件完成，禁止直连对方 Redis 键。

### 3.3 通信与事件契约

| 链路 | 方式 | 说明 |
|---|---|---|
| 前端 → 网关 → 服务 | REST/JSON | 路径前缀路由，`/api/**` 不变 |
| seckill → activity | 同步 REST（OpenFeign）+ 本地短时缓存 | 活动信息读多写少 |
| seckill → order | 异步事件（Redis Stream，后续可换 Kafka） | 复用 `seckill:order:stream` 全部机制 |
| order → analytics | 异步事件 `order.created` | 事件体：`orderNo/activityId/productId/userId/seckillPrice/requestTime` + `traceId` + `eventVersion` |
| 服务间管理操作 | 内部 REST + 内部令牌 | 不暴露到网关公网路由 |

**事件规范（P4 落地）**：字段全字符串化（沿用"明文 Map 防 Base64"教训）、携带 `eventVersion` 与 `traceId`、消费端幂等（唯一键或 NX）。

### 3.4 数据所有权速查

| 数据 | 唯一写入方 | 读取方 |
|---|---|---|
| `product` / `product:detail:*` | product-service | 前端、ai-service（目录） |
| `seckill_activity` / `seckill:activity:*` | activity-service | seckill-service（校验）、前端、ai-service |
| `seckill:stock:*` / `seckill:user:*` / `rate:limit:*` | seckill-service | analytics（只读 stock 供指标） |
| `seckill_order` / `seckill_message_log` | order-service | analytics（对账）、前端 |
| `seckill:rank:*` / `seckill:metric:*` / `seckill_activity_snapshot` | analytics-service | 前端、后台 |
| `user_account` / `user_session` | auth-service | 各服务仅信任网关解析出的用户身份 |

---

## 四、技术栈选型（对应需求 3）

### 4.1 选型总表

| 维度 | 选型 | 理由 | 备选 / 不选 |
|---|---|---|---|
| 服务框架 | Spring Boot 3.3.4 + **Spring Cloud 2023.0.x** | 与现有版本严格对齐，迁移零重写 | — |
| 注册 + 配置中心 | **Nacos 2.x**（一体） | Docker 一键、控制台可视化、教学友好 | Consul + Spring Cloud Config（组件多） |
| API 网关 | **Spring Cloud Gateway** | 原生集成、路由/过滤器/限流开箱 | Nginx 直连（无统一鉴权与观测） |
| 同步通信 | OpenFeign + LoadBalancer | 复用现有 REST 契约与 Result 信封 | gRPC（本项目收益不足，仅了解） |
| 异步通信 | **Redis Stream 起步**（已有）→ 进阶 Kafka | 复用 XADD/XREADGROUP/PEL/死信全部资产 | 直接上 Kafka/RocketMQ（运维重） |
| 分布式事务 | **不用 2PC**；本地事务 + 事件 + 补偿 + 对账 | R2/R5 死信补偿模式已实证 | Seata（仅对比讲解） |
| 限流熔断 | **Sentinel**（网关 + 服务） | 控制台可视化，与 Nacos 同生态 | Resilience4j（更轻但无可视化） |
| 链路追踪 | Micrometer Tracing + OpenTelemetry + Zipkin | Spring Boot 3 原生 | SkyWalking（一体化但更重） |
| 日志聚合 | Logback JSON + Filebeat → Loki + Grafana | 本机资源可控 | ELK（组件重，可选进阶） |
| 监控告警 | Actuator + Prometheus + Grafana + Alertmanager | 社区标准 | — |
| 容器编排 | Docker Compose（教学）；K8s 认知级 | 单机内存有限，profiles 分级启动 | 全量 K8s（本轮不做） |
| CI/CD | GitHub Actions（或 Jenkins） | 免费、可演示流水线 | — |
| 测试 | JUnit5 + Testcontainers + 契约测试（可选）+ JMeter（已有） | 服务独立后必须容器化依赖 | — |
| 数据库迁移 | Flyway（延续 `db/migration/V*.sql`） | 已有迁移雏形 | — |
| 分布式 ID | 现有 `IdGenerator`（单机）→ 号段/雪花服务 | 多实例后需中心化唯一 | UUID（破坏可读性） |

### 4.2 与现状的兼容性

- **版本不动**：Spring Boot 3.3.4 / JDK 17 / MySQL 8 / MyBatis-Plus 3.5.7 / Redisson 3.34.0 全部沿用；
- **配置不动**：`.env` + `application.yaml` 结构保留（spring-dotenv 继续生效，见 9.7 工作目录说明）；
- **契约不动**：`Result` 信封、错误码表、接口路径保持，前端零业务改动。

### 4.3 部署形态（Compose profiles + 内存预算）

| profile | 内容 | 适用 | 建议内存 |
|---|---|---|---|
| `core` | MySQL + Redis + Nacos + Gateway + seckill + order | 日常开发/演示 | ≈ 4 GB |
| `full` | core + auth/product/activity/analytics/ai + 观测三件套 | 联调/压测 | ≈ 8~10 GB |
| `obs` | Prometheus + Grafana + Loki + Zipkin | 观测演练 | ≈ 2 GB |

---

## 五、开发阶段规划（对应需求 4）

### 5.1 阶段总表

| 阶段 | 目标 | 关键交付物 | 验收标准 | 回退点 |
|---|---|---|---|---|
| **P0 边界治理** | 模块化单体 | 多模块工程 + 包边界 + ArchUnit 规则 | `mvn clean test` 92 用例全绿；单进程启动；规则全过 | 目录/包调整，随时可回 |
| **P1 基础设施** | 基建先行 | Nacos/Gateway/多 schema/Compose/追踪/监控骨架 | 网关代理单体成功；一条请求 traceId 贯通；Grafana 出图 | 基建独立，不影响单体 |
| **P2 边缘抽取** | 首个独立服务 | auth-service、ai-service、网关统一鉴权 | 登录/注册/会话、AI 对话独立部署；前端只换 baseURL | 网关切回单体路由 |
| **P3 商品与活动** | 主数据域独立 | product-service、activity-service 独立库与缓存所有权 | 缓存三防护与预热并发回归通过；双写对账一致 | 双写 + 读开关 |
| **P4 核心交易** | 跨服务交易闭环 | seckill-service、order-service、`order.created` 事件、数据迁移 | 200 并发无超卖（跨服务）；死信/回放/对账可用；JMeter 5000 复测 | 秒杀流量切回单体 |
| **P5 读模型收口** | CQRS 完成 | analytics-service 事件驱动读模型；单体降级/下线 | 榜单先后序断言通过（复用 Day1 用例）；指标与 DB 核对一致 | 读模型回退到 order 内计算 |
| **P6 质量与运维** | 可运维交付 | CI/CD、灰度、告警、混沌演练、Runbook | 注入"order 宕机 / Redis 不可用 / 网络延迟"三类故障按预期降级可恢复 | — |

### 5.2 P1~P6 任务要点

- **P1**：Compose 基建 → Nacos 命名空间（dev/staging/prod）→ Gateway 空路由 → 日志 JSON 化 → Micrometer+OTel 接入 → Prometheus 抓取 → 统一 `traceparent` 透传 → 单体注册进 Nacos（作为"遗留服务"）。
- **P2**：抽 auth（含 `PasswordEncoderConfig`、`UserTokenRegistry`、会话与 CSRF）与 ai（`AiLlmProperties`、`LlmChatClient`）；网关统一鉴权过滤器；前端 `VITE_API_BASE_URL` 切网关。
  - **P2 实施小结（2026-10-14 完成，进度与提交见 `docs/plan.md`）**：`flash-sale-auth`（:8082，auth 域实现下沉为库 `flash-sale-auth-core`）与 `flash-sale-ai`（:8083）已独立部署并注册 Nacos；网关精确路由 `/api/auth/**`、`/api/admin/users/**`、`/api/support/**` 到对应服务，并新增**准入鉴权**（管理令牌全量校验 + 用户态凭证存在性，拒绝时返回同形态 `40100` 信封）；服务侧 `ApiAccessInterceptor` 保留为**纵深防御**（直连服务仍受保护），CSRF / 会话有效性 / 资源归属等细校验仍在服务内完成。全量回归 124 用例绿线。
  - **P2 未做（明确留待后续）**：网关侧的**会话有效性核验**（introspection 或 JWT 无状态化）、auth 域 schema 独立与双写迁移（P3）、`/api/seckill/**` 与 `/api/admin/{seckill,products,files,metrics}` 仍在单体（P3/P4 拆出）。
- **P3**：product 独立 schema + 缓存键所有权 + OSS 凭据；activity 独立 schema + 预热锁；seckill-service 提供内部"库存初始化/重置"接口；双写开关 + 对账脚本。
- **P4**：seckill 独立 + order 独立；`order.created` 事件契约与版本化；死信回放跨服务改造；数据迁移三阶段（双写 → 切读 → 下线旧表）；JMeter 全链路复测。
- **P5**：analytics 消费 `order.created` 建读模型（ZSet NX 幂等）；指标埋点从 execute 侧改为事件 + 本地计数；单体退化为后台只读聚合或下线。
- **P6**：CI/CD 流水线（构建→测试→镜像→部署→灰度）；Prometheus 告警规则；混沌演练（kill order / 停 Redis / 注入延迟）；Runbook（回滚、对账、死信处理）。

---

## 六、质量与运维（对应需求 6）

### 6.1 测试策略

| 层 | 工具 | 覆盖对象 |
|---|---|---|
| 单元 / 切片 | JUnit5 + Mockito | 域内逻辑、Lua 脚本契约（现存用例直接搬） |
| 集成 | Testcontainers（MySQL/Redis/Nacos） | 服务内 + 服务间真实依赖 |
| 契约 | Spring Cloud Contract / Pact（可选） | Feign 调用方与提供方契约漂移 |
| 端到端 | JMeter（已有）+ Playwright | 秒杀主链、后台链路 |
| 混沌 | ChaosBlade 或手工 kill/delay | 熔断、超时、降级、补偿路径 |

### 6.2 CI/CD 流水线

```
push/PR → 静态检查 → 受影响模块单测 → 构建镜像（多阶段 Dockerfile）
       → Testcontainers 集成测试 → 推送制品 → 部署 dev → 契约 + 冒烟
       → 手动放行 → staging（灰度 5%→50%→100%）
```

- 分支/环境：`main`（可发布）/ `feature/*`；`dev / staging / prod` 三环境；
- 回滚：镜像版本回退 + 网关权重归零（≤1 分钟）；
- 数据库迁移随流水线执行（Flyway），迁移脚本与代码同版本发布。

### 6.3 日志 / 追踪 / 监控

- **traceId 三处贯通**：HTTP Header（`traceparent`）→ 服务内 MDC → **Stream 消息体携带**（消费者续接链路，本项目独有考点）；
- 结构化日志（JSON）+ 关键业务审计日志（下单 / 回放 / 重置，记录操作人与结果）；
- `requestId` 契约字段保留，作为 traceId 的展示形态之一；
- 指标：QPS、P99、错误率、库存余量、Stream 积压、PEL 待处理、死信数、Redis 连接池。

### 6.4 容错与一致性

- **超时预算**：网关 3s / 服务间 1s / Redis 200ms，逐层收敛；
- **重试**：仅幂等操作，次数有界（GET、XADD 前后已补偿）；
- **熔断降级**：activity 挂 → seckill 用本地活动副本短时兜底；analytics 挂 → 榜单/指标提示暂不可用，不影响下单；ai 挂 → 仅客服不可用；
- **兜底三件套（继承）**：DB 唯一键幂等 / 死信补偿（回补库存 + 释放令牌）/ 对账任务；
- **禁止跨服务事务**：一律"本地事务 + 事件 + 补偿"，事件用 Outbox（P4 引入）保证不丢。

### 6.5 SLO 候选

| 指标 | 目标 |
|---|---|
| 秒杀接口 P99 | ≤ 800 ms |
| 订单落库延迟 P99 | ≤ 3 s |
| 核心接口可用性 | ≥ 99.9%（演示口径） |
| 死信积压 | 0（告警阈值 > 0 持续 5 分钟） |

---

## 七、风险与应对（对应需求 7）

| 风险 | 影响 | 缓解 | 兜底 |
|---|---|---|---|
| 分布式事务（库存 vs 订单） | 超卖 / 丢单 | 禁止跨服务事务；Lua 预扣 → 事件 → 补偿；Outbox 保证事件不丢 | 对账任务 + `DeadLetterReplayService` 人工回放 |
| 数据一致性（缓存 / 读模型延迟） | 展示与 DB 不一致 | 单一写所有者；事件幂等（NX）；写后失效缓存 | 定时对账 + 前端标注"以服务端为准" |
| 服务依赖雪崩 | 全站不可用 | 超时 + 熔断 + 舱壁；依赖分级（交易链 > 榜单链） | 网关统一降级开关 |
| 拆分过度 / 运维成本 | 单人维护不动 | 8 服务封顶；低价值域先合后拆；Compose profiles | 回退模块化单体（P0 工程仍在） |
| 数据迁移丢数 / 错数 | 金额与库存失真 | 双写 + 影子读 + 对账；迁移前后快照比对 | 反向同步脚本 + 唯一键兜底 |
| 观测缺失排障困难 | 故障定位小时级 | **P1 先建可观测再拆服务**（顺序不可颠倒） | 临时降级为单体日志排查 |
| Redis 单点 / 键污染 | 一个键写错全站 | 键命名空间化 → 核心服务独立实例；继承 S2 白名单与密码配置 | 实例级隔离 |
| 为拆而拆（教学价值稀释） | 时间浪费 | 每阶段设"可停点"，量化验收不达标不进下一阶段 | — |
| 本机资源不足 | 起不动多服务 | Compose profiles；容器内存限额；开发期只起 `core` | 合并部署 |
| 时间与精力 | 半途而废 | 沿用"按天推进 + 每阶段验收 + plan.md 更新"节奏 | 任意阶段停下仍是完整系统 |

---

## 八、时间线与里程碑（对应需求 8）

### 8.1 总预估：约 29 个工作日（每天 2~3 小时 ≈ 6 周业余；全职 ≈ 1.5 周）

| 阶段 | 预估 | 里程碑 | 关键交付物 |
|---|---|---|---|
| P0 边界治理 | 4 天（较初版 +1 天：包级重命名） | **M1 模块化单体** | 12 模块工程 + 包边界 + 12 条 ArchUnit 规则 + 92 用例全绿 |
| P1 基础设施 | 3 天 | **M2 基建就绪** | Nacos/Gateway/Compose/追踪/监控，一条请求 traceId 贯通 |
| P2 边缘抽取 | 4 天 | **M3 首个服务上线** | auth + ai 独立部署，前端只改 baseURL |
| P3 商品与活动 | 5 天 | **M4 主数据域独立** | product/activity 独立库与缓存所有权，双写对账一致 |
| P4 核心交易 | 6 天 | **M5 跨服务交易闭环** | 200 并发无超卖 + 死信回放 + 对账 + JMeter 5000 复测 |
| P5 读模型收口 | 3 天 | **M6 CQRS 完成** | 榜单/指标事件驱动，全链路演示闭环 |
| P6 质量运维 | 4 天 | **M7 可运维交付** | CI/CD + 告警 + 混沌演练报告 + Runbook |

### 8.2 与单体计划的衔接

| 单体资产 | 微服务阶段去向 |
|---|---|
| 第 1~3 阶段 Lua / Stream / PEL / 死信 / 补偿 | 原样进入 seckill / order 服务（仅换部署单元） |
| 第 4 阶段榜单 / 指标 / 快照 | P5 演进为 analytics 读模型 |
| `scripts/jmeter` 5000 并发脚本 | P4 复测基准（改指向网关） |
| `docs/interface.md` 契约 + `Result` 信封 | 网关路由与契约冻结依据 |
| 92 用例测试基线 | P0 起按模块归位，作为每阶段回归红线 |
| S2 白名单 / `.env` 密钥 / R3 鉴权 | 升级为网关统一鉴权 + 服务间内部令牌 |
| 用户账号体系（未提交部分） | P2 归入 auth-service |

### 8.3 可停点（教学演练的安全边界）

- 停在 **M1**：完整单体 + 清晰模块边界，作为"架构治理"作品；
- 停在 **M3**：单体 + 2 个独立服务，可演示注册中心/网关/配置中心；
- 停在 **M5**：完整交易链路跨服务，可演示分布式一致性；
- M6/M7 为加分项（读模型与可运维）。

---

## 九、P0 详细任务卡（模块化单体）

### 9.1 P0 目标与验收

**目标**：在不改变运行形态（仍单进程 :8081）的前提下，把单体拆成 12 个 Maven 模块，建立包级边界与自动化架构约束，为后续进程级拆分铺路。

**验收标准（全部满足）**

1. `.\mvnw.cmd clean test` → 92 用例 BUILD SUCCESS（与基线一致）；
2. 每个模块可独立编译/测试：`.\mvnw.cmd -pl flash-sale-user test`；
3. ArchUnit 12 条规则全部通过（见 9.8）；
4. `.\mvnw.cmd -pl flash-sale-bootstrap -am spring-boot:run` 正常启动，`/api/health` 三 UP；
5. 前端零改动，秒杀演示链路（创建活动 → 预热 → execute → 订单 CREATED → 榜单 → 指标）回归通过；
6. 无跨域循环依赖；无空的遗留目录（`task/`、`service/order/`、`service/stream/`、`controller/order/`、`service/ratelimit/`）；
7. Git 历史可追溯（文件用 `git mv` / IDEA Refactor 迁移），每步一提交。

### 9.2 目标目录树

```
E-commerceFlashSaleSystem/                     # 聚合根（mvnw / .env / uploads / frontend 同级不变）
├─ pom.xml                                     # 父 POM（packaging=pom）
├─ mvnw / mvnw.cmd / .mvn/
├─ .env / .env.example
├─ uploads/                                    # 本地图片目录（P0 不动）
├─ flash-sale-common/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/common/**        (包名不变)
├─ flash-sale-contracts/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/contracts/**
├─ flash-sale-domain-model/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/domain/{entity,enums}/**   (包名不变)
├─ flash-sale-infra/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/infra/**
├─ flash-sale-user/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/user/**          (+ resources/mapper/)
├─ flash-sale-product/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/product/**       (+ resources/mapper/)
├─ flash-sale-activity/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/activity/**      (+ resources/mapper/)
├─ flash-sale-seckill/
│  ├─ src/main/java/com/ghb/ecommerceflashsalesystem/seckill/**
│  └─ src/main/resources/lua/*.lua
├─ flash-sale-order/
│  ├─ src/main/java/com/ghb/ecommerceflashsalesystem/order/**
│  └─ src/main/resources/mapper/*.xml
├─ flash-sale-analytics/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/analytics/**     (+ resources/mapper/)
├─ flash-sale-ai/
│  └─ src/main/java/com/ghb/ecommerceflashsalesystem/ai/**
└─ flash-sale-bootstrap/
   ├─ src/main/java/com/ghb/ecommerceflashsalesystem/bootstrap/**     (启动类 + 过渡装配)
   ├─ src/main/resources/{application.yaml, db/**, static/**, templates/}
   └─ src/test/java/com/ghb/ecommerceflashsalesystem/**               (跨域集成测试)
```

### 9.3 模块依赖矩阵（ArchUnit 依据）

| 模块 | 允许依赖（编译期） |
|---|---|
| common | 无（仅 jackson-annotations / jakarta.validation-api） |
| contracts | common |
| domain-model | common |
| infra | common、contracts（共享消息发布器）、domain-model、Spring Web/Redis/Redisson/MyBatis-Plus |
| user | common、domain-model、infra |
| product | common、domain-model、infra |
| activity | common、domain-model、infra、product（活动创建校验商品存在） |
| seckill | common、contracts、domain-model、infra、activity |
| order | common、contracts、domain-model、infra、analytics（落单成功后写榜单） |
| analytics | common、contracts、domain-model、infra |
| ai | common、domain-model、infra、product、activity |
| bootstrap | 全部模块（唯一装配层） |

> 依赖方向固定：**业务域不得反向依赖 bootstrap**；域间只走上表允许边，`seckill ↔ order` 只能通过 `contracts`。

### 9.4 现有代码 → 模块迁移映射表（逐文件）

| 目标模块 | 现有文件（包路径） | 目标包 |
|---|---|---|
| **common** | `common/api/Result`、`common/api/ResultCode`、`common/exception/BusinessException`、`common/util/{IdGenerator, OrderNoGenerator, RequestIdUtil}`、`common/constant/CacheKeyConstant`；**实施补充（Day 3）**：`domain/dto/response/PageResponse` 上移 `common/api`（跨域分页响应，避免域模块反向依赖 bootstrap） | 不变（PageResponse → `common.api`） |
| **infra** | `common/api/PageResult`（依赖 MP 的 Page）、`common/exception/GlobalExceptionHandler`（Web 关注点）、`config/{RedisConfig（剥脚本 Bean）, RedissonConfig, MybatisPlusConfig, MybatisPlusMetaObjectHandler}` | `infra.**` |
| **contracts** | `domain/dto/message/SeckillOrderMessage` | `contracts.message` |
| **domain-model** | `domain/entity/*`（7）、`domain/enums/*`（5） | 不变 |
| **user** | `controller/auth/UserAuthController`、`controller/admin/AdminUserController`、`service/user/{UserAccountService, UserSessionService}`、`mapper/{UserAccountMapper, UserSessionMapper}`、`dto/request/{LoginRequest, RegisterRequest, AdminUserCreateRequest, AdminUserUpdateRequest}`、`dto/response/{AuthResponse, RegisterResponse, UserProfileResponse}`、`config/{UserTokenRegistry, PasswordEncoderConfig}`；**实施补充（Day 3）**：`config/SecurityProperties` 随会话域归位（`UserSessionService` 依赖，原白名单项提前） | `user.**` |
| **product** | `controller/product/ProductController`、`controller/admin/{AdminProductController, AdminFileController}`、`service/product/ProductService`、`service/impl/ProductServiceImpl`、`service/cache/ProductCacheService`、`service/storage/**`、`mapper/ProductMapper`、`dto/request/ProductRequest`、`vo/ProductVO`、`config/FileStorageWebConfig` | `product.**` |
| **activity** | `service/seckill/SeckillActivityService`、`service/impl/SeckillActivityServiceImpl`、`service/cache/SeckillCacheService`、`mapper/SeckillActivityMapper`、`dto/request/ActivityRequest`、`dto/response/ActivityCheckResponse`（Day 3 补充：check 由活动域产出，Day 4 的 seckill 经 seckill→activity 取用）、`vo/{SeckillActivityVO, ActivityItemVO}`、`controller/admin/AdminSeckillController`（→ 过渡白名单，见 9.10） | `activity.**` |
| **seckill** | `controller/seckill/{SeckillExecuteController, SeckillActivityController}`、`service/seckill/SeckillService`、`service/impl/SeckillServiceImpl`、`dto/request/SeckillRequest`、`dto/response/SeckillResponse`；**实施调整（Day 4）**：`stream/producer/SeckillOrderStreamProducer` 归 `infra.messaging`（seckill 与 order 死信回放共用，避免 order→seckill 边）；`lua/*.lua` 与 3 个 `RedisScript` Bean 暂留 `infra`（P4 收口为 seckill-service 内部接口）；`ActivityCheckResponse` 归 `activity` | `seckill.**` |
| **order** | `stream/consumer/SeckillOrderConsumer`、`stream/scheduler/StreamConsumerScheduler`、`controller/seckill/OrderController`、`service/seckill/DeadLetterReplayService`、`mapper/{SeckillOrderMapper, SeckillMessageLogMapper}`、`dto/request/DeadLetterReplayRequest`、`resources/mapper/{SeckillOrderMapper, SeckillMessageLogMapper}.xml` | `order.**` |
| **analytics** | `controller/rank/RankController`、`controller/admin/AdminMetricsController`、`service/rank/SeckillRankService`、`service/impl/SeckillRankServiceImpl`、`service/metrics/MetricsService`、`service/impl/MetricsServiceImpl`、`mapper/SeckillActivitySnapshotMapper`、`vo/{RankEntryVO, ActivityMetricsVO}` | `analytics.**` |
| **ai** | `controller/support/AiSupportController`、`service/ai/**`（4）、`service/impl/AiChatServiceImpl`、`dto/request/AiChatRequest`、`vo/ChatReplyVO`、`config/AiLlmProperties` | `ai.**` |
| **bootstrap** | `ECommerceFlashSaleSystemApplication`、`application.yaml`、`resources/{db/**, mapper/ProductMapper.xml+SeckillActivityMapper.xml, static/**, templates/}`、`controller/health/HealthController`、跨域集成测试、过渡装配组件 | `bootstrap.**` |

**顺带清理（P0 内完成）**

| 项 | 处理 |
|---|---|
| 空目录 `task/`、`service/order/`、`service/stream/`、`controller/order/`、`service/ratelimit/` | 删除（如后续需要调度，统一放 `order.scheduler`） |
| `common/api/PageResult` 与 `dto/response/PageResponse` 双分页结构 | 全量搜引用：无引用者删除；有引用者统一为 `PageResponse`（前端契约字段 `records/page/pageSize/total/pages`） |
| `domain/dto/response` 与 `vo` 命名混用 | P0 不做大改，登记为 P3 清理项 |

### 9.5 父 POM 拆解

`E-commerceFlashSaleSystem/pom.xml` 从"应用 POM"改为"父 POM"（`packaging=pom`）：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>

  <!-- 继续继承 Boot 父，保证所有三方版本与现状一致 -->
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>com.ghb</groupId>
  <artifactId>flash-sale-parent</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>flash-sale-common</module>
    <module>flash-sale-contracts</module>
    <module>flash-sale-domain-model</module>
    <module>flash-sale-infra</module>
    <module>flash-sale-user</module>
    <module>flash-sale-product</module>
    <module>flash-sale-activity</module>
    <module>flash-sale-seckill</module>
    <module>flash-sale-order</module>
    <module>flash-sale-analytics</module>
    <module>flash-sale-ai</module>
    <module>flash-sale-bootstrap</module>
  </modules>

  <properties>
    <java.version>17</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <redisson.version>3.34.0</redisson.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <aliyun-oss.version>3.17.4</aliyun-oss.version>
    <spring-dotenv.version>4.0.0</spring-dotenv.version>
    <archunit.version>1.3.0</archunit.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- 内部模块（版本统一跟随父工程） -->
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-common</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-contracts</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-domain-model</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-infra</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-user</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-product</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-activity</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-seckill</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-order</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-analytics</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-ai</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.ghb</groupId><artifactId>flash-sale-bootstrap</artifactId><version>${project.version}</version></dependency>

      <!-- 三方（Boot 父未管理的部分） -->
      <dependency><groupId>org.redisson</groupId><artifactId>redisson</artifactId><version>${redisson.version}</version></dependency>
      <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot3-starter</artifactId><version>${mybatis-plus.version}</version></dependency>
      <dependency><groupId>com.aliyun.oss</groupId><artifactId>aliyun-sdk-oss</artifactId><version>${aliyun-oss.version}</version></dependency>
      <dependency><groupId>me.paulschwarz</groupId><artifactId>spring-dotenv</artifactId><version>${spring-dotenv.version}</version></dependency>
      <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId><version>${archunit.version}</version><scope>test</scope></dependency>
    </dependencies>
  </dependencyManagement>

  <!-- 全模块共用依赖：Lombok + 测试基座（版本由 Boot 父管理） -->
  <dependencies>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <pluginManagement>
      <plugins>
        <!-- 仅 bootstrap 模块声明使用；此处只统一配置 -->
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <configuration>
            <!-- 以聚合根为运行目录：保证 .env 与 uploads/ 相对路径解析不变 -->
            <workingDirectory>${maven.multiModuleProjectDirectory}</workingDirectory>
            <excludes>
              <exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude>
            </excludes>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <configuration>
            <!-- 子模块跑测试时同样以聚合根为工作目录，spring-dotenv 才能读到根级 .env -->
            <workingDirectory>${maven.multiModuleProjectDirectory}</workingDirectory>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

**父 POM 关键点说明**

1. **继续继承 `spring-boot-starter-parent`**：三方版本零变更，回归风险最小；
2. **`dependencyManagement` 只做版本管理**，不加实体依赖（除 Lombok + starter-test 这类全模块通用项）；
3. **`spring-boot-maven-plugin` 严禁放父 `<build><plugins>`**，否则所有库模块都会被 repackage 成可执行 fat jar；只在 `flash-sale-bootstrap` 声明；
4. **`${maven.multiModuleProjectDirectory}`** 由 mvnw/mvn 从聚合根启动时注入，统一 surefire 与 `spring-boot:run` 的工作目录 = `.env` / `uploads/` 的解析位置不变（**Day 1 必须实测验证，若插件版本行为不符，退路：bootstrap 与各测试模块内保留 `.env` 副本（已被 .gitignore 忽略）或显式配置 dotenv 目录**）。

### 9.6 子模块 POM 模板

**库模块通用**（以 `flash-sale-common` 为例，零框架依赖）：

```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.ghb</groupId>
    <artifactId>flash-sale-parent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
  </parent>
  <artifactId>flash-sale-common</artifactId>
  <dependencies>
    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-annotations</artifactId></dependency>
    <dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId></dependency>
  </dependencies>
</project>
```

**infra**：

```xml
<artifactId>flash-sale-infra</artifactId>
<dependencies>
  <dependency>flash-sale-common</dependency>
  <dependency>flash-sale-domain-model</dependency>
  <dependency>spring-boot-starter-web</dependency>
  <dependency>spring-boot-starter-data-redis</dependency>
  <dependency>redisson</dependency>
  <dependency>mybatis-plus-spring-boot3-starter</dependency>
  <dependency>me.paulschwarz:spring-dotenv</dependency>
</dependencies>
```

**seckill**（示例业务模块）：

```xml
<artifactId>flash-sale-seckill</artifactId>
<dependencies>
  <dependency>flash-sale-common</dependency>
  <dependency>flash-sale-contracts</dependency>
  <dependency>flash-sale-domain-model</dependency>
  <dependency>flash-sale-infra</dependency>
  <dependency>flash-sale-activity</dependency>
</dependencies>
```

**bootstrap**（唯一可执行模块）：

```xml
<artifactId>flash-sale-bootstrap</artifactId>
<dependencies>
  <dependency>flash-sale-{common,contracts,domain-model,infra,user,product,activity,seckill,order,analytics,ai}</dependency>
  <dependency>com.mysql:mysql-connector-j（runtime）</dependency>
  <dependency>com.tngtech.archunit:archunit-junit5（test）</dependency>
</dependencies>
<build>
  <plugins>
    <plugin>org.springframework.boot:spring-boot-maven-plugin</plugin>
  </plugins>
</build>
```

**product** 额外声明 `aliyun-sdk-oss`；**ai** 无额外依赖；**各域模块** 按需声明 `mysql-connector-j`（仅需要跑 DB 集成测试的模块，`scope=test`）。

### 9.7 配置与资源调整清单

| # | 项 | 现状 | P0 调整 |
|---|---|---|---|
| 1 | `@MapperScan` | `"com.ghb.ecommerceflashsalesystem.mapper"` | 改为 5 个域包：`user.mapper / product.mapper / activity.mapper / order.mapper / analytics.mapper` |
| 2 | `mybatis-plus.mapper-locations` | `classpath:mapper/*.xml` | 改 `classpath*:mapper/*.xml`（多模块 jar 需通配扫描） |
| 3 | `mybatis-plus.type-aliases-package` | `...domain.entity` | 实体包不变，配置**无需改**（XML 均用全限定名，可顺带复核） |
| 4 | `.env` 与 `uploads/` | 以进程工作目录解析 | 由父 POM 的 surefire / spring-boot `workingDirectory` 统一为聚合根（Day 1 实测） |
| 5 | Lua 脚本 | `resources/lua/*.lua` + `RedisConfig` 内 3 个 Bean | 脚本迁 `flash-sale-seckill/resources/lua/`；Bean 抽到 `seckill.config.SeckillScriptConfig`（`ClassPathResource("lua/...")` 解析不变） |
| 6 | Mapper XML | `resources/mapper/*.xml`（3 个） | 随域迁移（product / activity / order），文件名不变 |
| 7 | `db/schema.sql`、`db/migration/*.sql` | 单体资源 | 暂留 bootstrap（P3/P4 数据迁移时再按域拆分） |
| 8 | `static/`、`templates/` | 单体资源 | 暂留 bootstrap |
| 9 | `logging.level` 配置键 | `com.ghb.ecommerceflashsalesystem: ERROR` | 包前缀不变，无需改 |
| 10 | 前端 dev proxy / `VITE_API_BASE_URL` | 指向 :8081 | P0 不变（P2 才切网关） |

### 9.8 ArchUnit 规则清单（12 条）

依赖：父 POM `dependencyManagement` 已含 `archunit-junit5`，`flash-sale-bootstrap` test 范围引入；规则类放 `flash-sale-bootstrap/src/test/java/.../architecture/ArchitectureTest.java`。

| # | 规则 | ArchUnit 表达要点 | 防的问题 |
|---|---|---|---|
| R1 | common 保持零框架 | `noClasses().that().resideInAPackage("..common..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","com.baomidou..","org.redisson..")` | 共享层腐化 |
| R2 | contracts / domain-model 不得依赖业务域与 infra | `noClasses().that().resideInAnyPackage("..contracts..","..domain..").should().dependOnClassesThat().resideInAnyPackage("..infra..","..user..","..product..","..activity..","..seckill..","..order..","..analytics..","..ai..","..bootstrap..")` | 契约反向依赖 |
| R3 | service 不得依赖 controller | `noClasses().that().resideInAPackage("..service..").should().dependOnClassesThat().resideInAPackage("..controller..")` | 分层倒置 |
| R4 | controller 不得直接依赖 mapper | `noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAPackage("..mapper..")` | 绕过服务层 |
| R5 | bootstrap 不得被业务域依赖 | `noClasses().that().resideOutsideOfPackage("..bootstrap..").should().dependOnClassesThat().resideInAPackage("..bootstrap..")` | 装配层被反向引用 |
| R6 | 无跨域循环 | `slices().matching("com.ghb.ecommerceflashsalesystem.(*)..").should().beFreeOfCycles()` | 模块环 |
| R7 | seckill ↔ order 只走 contracts | `noClasses().that().resideInAPackage("..seckill..").should().dependOnClassesThat().resideInAPackage("..order..")` 及反向 | 交易链耦合 |
| R8 | ai 只允许依赖 product / activity | `noClasses().that().resideInAPackage("..ai..").should().dependOnClassesThat().resideInAnyPackage("..user..","..seckill..","..order..","..analytics..")` | 客服越界 |
| R9 | order 不得依赖 product / activity / seckill | `noClasses().that().resideInAPackage("..order..").should().dependOnClassesThat().resideInAnyPackage("..product..","..activity..","..seckill..")` | 消费端越界 |
| R10 | 命名规范 | controller 以 `Controller` 结尾、mapper 以 `Mapper` 结尾、service 接口以 `Service` 结尾；`..controller..` 类必须在 `..controller..` 包内 | 命名漂移 |
| R11 | 事务注解只在 service 层 | `methods().that().areAnnotatedWith(Transactional.class).should().beDeclaredInClassesThat().resideInAPackage("..service..")`（视现状放宽，可先 warn） | 事务边界失控 |
| R12 | 禁用字段注入与 `System.out` | `noFields().should().beAnnotatedWith(Autowired.class)` + `noClasses().should().accessField(System.class, "out")` | 注入风格与日志规范 |

**过渡豁免**：9.10 白名单中的 3 个组件用 `.ignoreDependency(...)` 或调整为允许规则；`archunit.properties` 设 `archRule.failOnEmptyShould=false`，避免空匹配误报。

### 9.9 逐日步骤（Day 1 ~ Day 4）

> 前置 0.5 天：`git status` 确认无未提交（有则先提交）→ `git tag mono-baseline-<date>` → 停 dev 后端 → `.\mvnw.cmd clean test` 记录 92 用例基线。

**Day 1｜多模块骨架（零行为变更）**

| 步骤 | 操作 | 验证 |
|---|---|---|
| 1 | 父 POM 改为 `flash-sale-parent`（聚合 12 模块），按 9.5 落地 | `.\mvnw.cmd -q -N validate` |
| 2 | 新建 11 个子模块目录与最小 POM（依赖暂空） | `.\mvnw.cmd -q -DskipTests test-compile` |
| 3 | **现有 `src/` 整体迁入 `flash-sale-bootstrap/`**（`git mv`，包名暂不动） | 同上 |
| 4 | bootstrap POM 声明 `spring-boot-maven-plugin`；父 POM 配置 surefire / run 的 `workingDirectory` | 启动冒烟：`/api/health` 三 UP |
| 5 | 全量回归 | `.\mvnw.cmd clean test` → 92 用例绿 |
| 6 | 提交 | `refactor(p0): 多模块骨架 — 源码整体迁入 bootstrap（零行为变更）` |

**Day 2｜共享层抽取 + 包重构**

| 步骤 | 操作 | 验证 |
|---|---|---|
| 1 | 抽 `common`（6 类，包名不变） | 全量编译 + 回归 |
| 2 | 抽 `domain-model`（entity 7 + enums 5，包名不变） | 同上 |
| 3 | 抽 `contracts`（`SeckillOrderMessage` → `contracts.message`） | 同上 |
| 4 | 抽 `infra`（`RedisConfig`/`RedissonConfig`/MybatisPlus 配置/`GlobalExceptionHandler`/`PageResult`）→ 包重命名为 `infra.**`（IDEA Refactor → Move） | 同上 |
| 5 | 处理双分页结构：`PageResult` vs `PageResponse` 引用排查，二选一 | 编译 + 接口回归 |
| 6 | 删除空目录 `task/`、`service/order/`、`service/stream/`、`controller/order/`、`service/ratelimit/` | `git status` 无残留 |
| 7 | 提交 | `refactor(p0): 抽取 common/contracts/domain-model/infra 模块` |

**Day 3｜业务域迁移（上半）**

| 步骤 | 操作 | 验证 |
|---|---|---|
| 1 | 抽 `user`（含 `UserTokenRegistry`、`PasswordEncoderConfig`、4 个 DTO、3 个 response、2 个 mapper） | `.\mvnw.cmd -pl flash-sale-user -am test` + 全量 |
| 2 | 抽 `product`（含 storage、缓存服务、`FileStorageWebConfig`、`aliyun-sdk-oss` 依赖、mapper XML） | 同上（重点回归缓存三防护测试） |
| 3 | 抽 `activity`（含 `SeckillCacheService`、`SeckillActivityServiceImpl`、mapper XML） | 同上（重点回归预热并发测试） |
| 4 | 每抽一个域提交一次 | `refactor(p0): 抽取 user/product/activity 模块` |

**Day 4｜业务域迁移（下半）+ 收薄 + ArchUnit**

| 步骤 | 操作 | 验证 |
|---|---|---|
| 1 | 抽 `seckill`（controller/service/producer + lua + `SeckillScriptConfig` 脚本 Bean） | 全量 + 并发防超卖用例 |
| 2 | 抽 `order`（consumer/scheduler/OrderController/DeadLetterReplayService/2 mapper + XML） | 全量 + 消费/死信回归 |
| 3 | 抽 `analytics`、`ai` | 全量 + 榜单/指标/AI 用例 |
| 4 | bootstrap 收薄：启动类 + `application.yaml` + `db/static/templates` + 跨域测试 + 过渡白名单（9.10） | 启动冒烟 + 前端秒杀演示链路 |
| 5 | 落地 ArchUnit 12 条规则 + 豁免项 | `.\mvnw.cmd -pl flash-sale-bootstrap test` 规则全绿 |
| 6 | 终验：全量回归 + 前端联调 + 打标签 | `.\mvnw.cmd clean test` 92 绿；`git tag p0-done` |
| 7 | 更新 `docs/plan.md` 第 5 阶段 P0 状态 | 文档同步 |
| 8 | 提交 | `refactor(p0): 业务域模块化 + ArchUnit 架构约束（M1 达成）` |

### 9.10 过渡白名单（P0 允许跨域，后续阶段收口）

| 组件 | 现状问题 | P0 处理 | 收口阶段 |
|---|---|---|---|
| `ApiAccessInterceptor` / `ApiAccessWebConfig` | 横切鉴权 + 管理/用户双令牌，跨 user 与 admin 双域 | 暂留 `bootstrap`，ArchUnit 显式豁免（`SecurityProperties` 已于 Day 3 归位 `user.config`） | P2（抽 auth-service 时归位；P1 后可改为网关过滤器） |
| `AdminSeckillController` | 一个控制器混合活动管理与死信回放（order 域） | 暂留 `bootstrap`，仅依赖 activity/order 的 Service 接口 | P4（拆 `AdminDeadLetterController` 到 order-service） |
| `HealthController` | 全局健康检查 | 暂留 bootstrap | P1（改为各服务 Actuator + 网关聚合） |
| `domain-model` 共享实体 | 多域共用一个实体模块（微服务反模式的前身） | 过渡接受，登记改造项 | P3/P4（各域私有实体 + contracts 替代） |

### 9.11 P0 风险与回退

| 风险 | 信号 | 应对 |
|---|---|---|
| `.env` 找不到导致 19 个 DB 用例 Error | 测试报 Access denied / 上下文失败 | 检查 surefire `workingDirectory`；退路：各模块保留 `.env` 副本（gitignore 已忽略 `.env*`） |
| `mapper-locations` 只扫到一个模块 XML | `Invalid bound statement` | 改 `classpath*:mapper/*.xml` 并核对各模块 resources 目录 |
| `@MapperScan` 漏包 | 同上 | 显式列 5 个域包；启动日志核对 Mapper 数量 |
| 包重命名引入隐性引用断裂 | 编译失败 | 只用 IDE Refactor（Move/Rename），禁止手改字符串；每步编译 |
| 测试归属错误导致跨域引用 | ArchUnit R3/R5 报错 | 单域测试随域迁移；跨域/全链路测试统一留 bootstrap |
| 一次性大搬家难以定位回归 | 用例红但不知来源 | **每个域一次提交**，出问题 `git checkout` 单个域回退 |
| Maven 环境问题（历史踩坑） | `ClassNotFoundException: ...classworlds...` | 统一用 `.\mvnw.cmd`（wrapper 3.9.16），不要用损坏的用户级 Maven |

**回退策略**：P0 全过程"每步绿 + 每步提交"，任一时刻可 `git reset --hard <上一步 commit>`；P0 结束打 `p0-done` 标签，P1 出问题可整体回到模块化单体。

### 9.12 P0 实施结果（2026-10-13 完成 · M1 达成）

**最终模块结构（13 个模块 = 父 POM + 12 子模块，主代码 106 个类）**

| 模块 | main 类数 | 说明 |
|---|---|---|
| flash-sale-common | 9 | Result / ResultCode / BusinessException / PageResponse / 工具 / 常量 / 鉴权常量 |
| flash-sale-contracts | 1 | SeckillOrderMessage（跨域消息契约） |
| flash-sale-domain-model | 12 | 7 实体 + 5 枚举（仅依赖 `mybatis-plus-annotation`） |
| flash-sale-infra | 6 | Redis / Redisson / MyBatis-Plus 配置、全局异常处理、消息发布器（infra.messaging） |
| flash-sale-user | 16 | 账号 / 会话 / CSRF / 令牌注册表 / 后台用户管理 |
| flash-sale-product | 14 | 商品 CRUD + 缓存三防护 + 图片存储（本地/OSS） |
| flash-sale-activity | 8 | 活动管理与预热编排（含预热/重置锁） |
| flash-sale-seckill | 6 | 滑动窗口限流 + Lua 原子扣减 + Stream 投递 |
| flash-sale-order | 10 | 消费落单 / 订单查询 / 消息日志 / 死信回放 + 订单查询端口适配 |
| flash-sale-analytics | 10 | 榜单 / 指标 / 快照 + `OrderQueryPort`（读模型端口） |
| flash-sale-ai | 9 | AI 客服（LLM 客户端适配独立在 `ai.client`） |
| flash-sale-bootstrap | 5 (+27 测试) | 启动类 + 4 个白名单装配组件 + 全部测试 |

**实施中相对原方案的关键调整（4 处）**

1. **解环替代方案**：原方案未预见 analytics ↔ order 双向依赖（榜单需要订单数据、订单落库要写榜单）。最终以**端口倒置**解决：`analytics.port.OrderQueryPort` 由 `order.adapter.OrderQueryAdapter` 实现，依赖方向保持单向，未引入运行期反射或循环依赖。
2. **共享技术资产留守 infra**：`SeckillOrderStreamProducer`、`lua/*.lua` 与 3 个 `RedisScript` Bean 被 seckill 与 order 共同使用，为保证"seckill ↔ order 无直接依赖"而保留在 infra；P4 抽取 seckill-service 时改为内部接口调用。
3. **白名单提前归位**：`SecurityProperties` 因被 `UserSessionService` 依赖，从"暂留 bootstrap"提前在 Day 3 归位 `user.config`；`ActivityCheckResponse` 归 `activity`。
4. **分层修复随行**：`OrderController` / `ProductController` 两处 controller → mapper 直连按 R4 收敛（新增 `OrderQueryService`、`ProductService.listOnShelf`）；LLM 客户端 3 类迁出 `service` 包（`ai.client`）。

**验证记录**

- `mvnw clean test` → **113 用例，0 Failures / 0 Errors，BUILD SUCCESS**（97 业务用例 + 16 条架构断言）；
- ArchUnit 断言覆盖计划中的 12 类约束（部分约束拆分为多条表达，如 seckill/order 双向隔离）；
- 运行形态与接口契约不变：bootstrap 仍是单进程 :8081，前端零改动；
- 标签：`p0-done`；随后可进入 **P1 基础设施**（Nacos / Gateway / Compose / 可观测）。

### 9.13 P1-1 实施记录（2026-10-13）

| 项 | 方案与现状 |
|---|---|
| 网关端口 | 默认 **8090**（`GATEWAY_PORT` 可覆盖）：本机 8080 已被外部服务占用（uvicorn / 疑似 WSL 端口转发），非本项目冲突；前端 `vite.config.js` 代理同步指向 8090 |
| 网关职责（P1-1） | `/api/**` 静态路由到单体 `http://localhost:8081`、CORS（`globalcors`）、traceId 注入与回填、请求耗时日志；`lb://` 动态路由留待 P1-2（Nacos） |
| traceId 方案 | **自研轻量三处贯通**（不依赖 OTel/Zipkin）：网关生成/透传 `X-Trace-Id` → 单体 `infra.web.TraceIdFilter` 写 MDC + 回填响应头 → `SeckillOrderStreamProducer` 写入消息体 `traceId` → `SeckillOrderConsumer` 恢复 MDC。P1-2 引入 OTel 后，该 id 可保留为业务侧链路号，或由 OTel traceId 取代 |
| 指标 | 单体与网关均暴露 `/actuator/health`、`/actuator/prometheus`；Prometheus 抓取与 Grafana 看板留待 P1-2 |
| 验证证据 | 同一 `stream-trace-001` 出现在三处：网关日志（reactor）· 单体生产线程 `http-nio-8081-exec-5`（订单消息已发送到 Stream）· 消费线程 `scheduling-1`（开始处理消息 → 订单落库成功并 ACK）；响应头 `X-Trace-Id` 唯一；全量 113 用例 BUILD SUCCESS |
