# 交付前最终审查报告（S2 / M 系列审查底稿 · 留档）

> 本文档是驱动 commit `c1f689e`（S2 Redis 加固 + M1~M10 审查整改）的**审查底稿**，由交付前审查会话产出（2026-09-09）。
> 因原始产出直接覆盖了 `docs/audit-report.md`（2026-09-08 全量审计 R1~R5/C1~C6/E1~E6 报告），经 panda 决定：**底稿改名留档于本文件并逐条标注复核状态；`audit-report.md` 恢复为 R/C/E 版**。
> 复核基准：2026-09-09 提交 `c1f689e` 之后 + Day 6 学习收口（`7e3d92c`）。文内 AI 密钥已打码（`.env` 中仍为真实值，未入 Git）。

---

## 〇、工程验证结果（原稿快照 + 复核）

| 项目 | 原稿结果（2026-09-09） | 复核现状 |
|---|---|---|
| `mvnw test-compile`（main + test 全量编译） | ✅ BUILD SUCCESS | ✅ 依旧成立 |
| 前端 `npm run build` | ✅ 4.72s 构建成功 | ✅ 依旧成立 |
| 运行中实例冒烟（health / activities） | ✅ `code=0`，MySQL/Redis 均 UP | ✅ 依旧成立 |
| 全量 `mvn test`（92 用例） | ⚠️ 原稿当时未重跑（8081 有 dev 后端占用） | ✅ **已补跑**：本会话停 dev 后全量 92 用例 / 0 Failures / 0 Errors / BUILD SUCCESS |

---

## 一、总体结论（保留 + 复核）

> 原稿结论："达到交付条件，可进入收尾与交付阶段"。

复核：该结论成立，且其"建议先处理"的两项严重问题（S1 密钥、S2 Redis 加固）与 M 系列**均已落地（`c1f689e`）**；"Day 6 复盘总结作为最后收尾产出"也已于 `7e3d92c` 完成。当前遗留仅剩非阻塞项（见 §五）。

---

## 二、严重问题（原稿判定"上线前必须处理"）

### S1 · AI 密钥明文硬编码残留
- **原稿位置**：`config/AiLlmProperties.java` 默认值 `sk-****`（已打码）
- **复核现状**：✅ **已清**。现 `AiLlmProperties.apiKey` 默认值为空串 `""`（运行期由 `${AI_LLM_API_KEY:}` 绑定）；该密钥 `git log -S` 全历史**无任何入库记录**，仅存在于 `.env`（gitignore）与本文档打码引用。
- **建议**：如该 Key 曾在演示中被真实外呼，去 DeepSeek 控制台**轮换一次**为佳（成本极低）；纯仓库卫生角度已无需处理。

### S2 · Redis 无认证 + GenericJackson 全类型反序列化（若对外部署）
- **原稿位置**：`application.yaml`（redis 无 password）、`RedisConfig.java`（`LaissezFaireSubTypeValidator` 无白名单）
- **复核现状**：✅ **已落地（`c1f689e`）**。`RedisConfig` 改为 `BasicPolymorphicTypeValidator` 白名单（实体包 + 常用 JDK 类型）；`application.yaml` 增 `spring.data.redis.password: ${REDIS_PASSWORD:}`（`.env` 可配，本地默认无密码）。
- **残留建议**（部署项，非代码）：对外/公网部署仍应给 Redis 设密码并绑内网/回环，用 `REDIS_PASSWORD` 注入。

---

## 三、一般问题 M1~M10（逐条复核，均已在 `c1f689e` 落地）

| # | 原稿问题 | 落地位置（`c1f689e`） | 复核状态 |
|---|---|---|---|
| M1 | execute 失败 `data.result` 折叠为 `REJECTED` | `SeckillExecuteController`（按 `ResultCode` 细分） | ✅ 已落地（40902→SOLD_OUT / 40901→DUPLICATED / 42900→RATE_LIMITED 语义） |
| M2 | `AiLlmProperties` model 默认与 yaml 不一致 | —（非 M 主项） | ✅ 已对齐：类默认与 yaml 现均为 `deepseek-chat`（yaml 注释说明 v4-flash 对复杂目录偶发空回复） |
| M3 | `requestId` 用 UUID，`RequestIdUtil` 成死代码 | `common/api/Result`（构造对齐 `RequestIdUtil` 时间戳格式） | ✅ 已落地（requestId 对齐 interface 2.3） |
| M4 | 已结束/未开始且缓存过期 → 误报"活动尚未预热" | `SeckillServiceImpl.execute` 未命中缓存分支 | ✅ 已落地（按实时时间窗细分拒绝，与 check 口径同源） |
| M5 | check 文档语义与实现（查令牌/限流）不符 | —（文档措辞取舍） | ✅ 采纳"文档措辞"方案：check/详情只判活动开放+实时库存，幂等/限流属 execute 行为；口径统一见 plan.md Day3/4「4.2 check 口径统一」 |
| M6 | interface.md / plan.md / lua 注释滞后 | `docs/interface.md`、plan E 卡、`rate_limit.lua` 头注释 | ✅ 已落地：40100 状态码、接口清单、缓存键双维化、E1-r/E6 表格状态均已同步（残余零星文案可按需） |
| M7 | AI 目录把已过期活动当"还有场次" | `AiChatServiceImpl`（目录过滤 + 状态文案实时推导） | ✅ 已落地 |
| M8 | 未预热活动在列表显示"抢购中" | `ActivityItemVO.preheated` 预热标记 + 前端按标记展示 | ✅ 已落地 |
| M9 | 订单查询无鉴权；详情页暴露预热 | `OrderController` 可选归属校验；预热入口收敛管理台 | ✅ 已落地（`users/{id}/orders` 鉴权令牌匹配见 R3 体系） |
| M10 | 管理能力无前端入口（我的订单等） | `ActivityDetailView`「我的抢购记录」面板 | ✅ 已落地（兑现 4.10 演示）；`reset-stock`/`dead-letters/replay` 保留 Postman/脚本路径并在 README/脚本注明（可选增强） |

---

## 四、建议优化（不阻塞交付，原样保留供后续跟进）

| # | 位置 | 内容 | 复核状态 |
|---|---|---|---|
| O1 | `SeckillServiceImpl` | `getActivityVO`/`convertToVO` 死代码 | 开放建议（重构时顺手清理） |
| O2 | `GlobalExceptionHandler` | HTTP 状态码混用，建议统一 | 开放建议 |
| O3 | `application.yaml` | `com.ghb...: ERROR` 压掉演示关键 INFO | 开放建议（演示档 INFO / 生产档 ERROR 抽 profile） |
| O4 | Redis 长期运行 | 榜单/metric/Stream 无 TTL，内存单边增长 | 开放建议（活动维度清理任务或注明生命周期） |
| O5 | `OssImageStorageService` | OSSClient 每次 new/shutdown | 开放建议（单例复用） |
| O6 | `UserTokenRegistry` | 动态注册无上限/过期清理、注册无频控 | 开放建议（公网需加） |
| O7 | `AiSupportController` | 匿名+无频控，消耗外部配额 | 开放建议（复用用户级限流） |
| O8 | `OrderNoGenerator`/`IdGenerator` | 单机唯一，多实例需分片/号段 | 开放建议（README 注明） |
| O9 | 前后端时区 | `LocalDateTime` 依赖 JVM/DB 同区 | 开放建议（跨时区统一 UTC 存储，已有注释约定） |
| O10 | 前端 `utils/format.js` | `STATUS_TEXT`/`ORDER_STATUS_TEXT` 未使用导出 | 开放建议（清理） |

---

## 五、需求缺口清单（复核更新）

1. **Day 6 收尾**：原稿列为未完成 → ✅ **已完成**（`7e3d92c`：`docs/redis-summary.md` + `docs/notework.md` + plan 收尾）。
2. **E6 挂起复现项**：图片上传「文件内容与图片格式不匹配」合法图片误拒 → ⏳ **仍挂起**（需失败图片样本，见 plan.md）。
3. **前端"我的秒杀订单"视图**：→ ✅ 详情页「我的抢购记录」面板已补（M10）；4.10 列表全量视图仍为可选增强。
4. **管理端死信回放 / 库存重置 UI**：保留 API + Postman/脚本路径，无页面 → 可接受（可选增强）。
5. **interface.md / plan.md 与实现对账**：→ ✅ M6 已同步；若后续再改动契约请顺手对账。

---

## 六、交付建议（复核更新）

- 原稿建议的「文档同步 + 小修」补丁（M1/M3/M4/M6/M7/M8/M9/M10、S1/S2）**已随 `c1f689e` 提交落地**；
- 全量回归 **92 用例 BUILD SUCCESS** 已在本会话停 dev 后补跑通过（原稿"留待空闲时执行"已兑现）；
- 项目 4 阶段 + 审计 + 交付收尾 + Day 6 学习收口全部完成；剩余为 §五 中 ⏳ 项（E6 复现）与可选增强，不再阻塞交付。
