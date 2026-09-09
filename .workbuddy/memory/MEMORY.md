# 项目长期记忆 — 轻量级电商秒杀系统

## 沟通约定（来自 docs/plan.md「必读」，本会话须遵守）

1. 称呼：代码编写过程中称呼开发者为 **panda**。
2. 语言：始终使用简体中文，不混用英文（代码语法除外）。
3. 指导方式：AI 作为导师引导+检查，代码由 panda 亲手写，导师**不直接代写**；仅阻塞性问题（配置错误/依赖冲突）可介入修复。
4. 开发环境：本地 MySQL 8.0 + WSL Docker 运行 Redis 7.0 + IntelliJ IDEA。应用端口 **8081**（application.yaml 已设）。
5. 工作流：导师分步布置 → panda 编码 → 导师审查 → panda 修正 → 确认通过。每完成一个 Day 更新一次 docs/plan.md。
6. 测试验证：每个功能模块完成必须 `mvn test` 保证 **BUILD SUCCESS**；收尾必须全量跑，不能只看单个测试类。
7. 问题记录：技术与修复方案及时记入 docs/plan.md「经验教训」，便于复盘。
8. 代码注释：panda 会写教学式个人理解注释，审查应忽略、不要求删除，只关注逻辑正确性。
9. plan.md 进度由导师实时维护，panda 完成后只需告知，不必手动更新。
10. **分工变更（2026-09-09 起，plan.md G 卡记录）**：后续功能由 AI 完成实现，panda 只做最终审查（此前为「panda 写码、导师只查不代写」；阻塞性环境问题仍由导师介入）。

## 关键技术约定

- 金额一律 `Long` 单位「分」；时间 UTC 存储、Asia/Shanghai 显示；ID 由应用生成（MyBatis-Plus `id-type: INPUT`），`seckill_order` 主键 AUTO。
- Redis 用 **database 1**，排查须 `redis-cli -n 1`（默认连 db0 会误判键不存在）。
- Redis 序列化用含 `JavaTimeModule` 的 ObjectMapper + 必须开 DefaultTyping；缓存键前缀统一收敛到 `CacheKeyConstant`。
- 缓存：基础 TTL 随机抖动防雪崩（300+random(60)s）；空值穿透标记为 `""`、TTL 60s，读取须区分「未命中/空值/真实值」三态。
- 实体/枚举：统一 `code`/`description` + `fromValue()`。
- 测试习惯：`@Transactional` 回滚防污染；缓存/锁用真实 Redis + Mapper 用 `@MockBean`；并发用 CountDownLatch + 超时兜底；清理 Redis 键判空写 `!keys.isEmpty()`（写反会永远清不掉）；Reset 会连 stub 一起清掉。
- 种子数据易过期/被测试改脏，跑预热等接口前确认活动 endTime 在未来；验证更新接口用专属测试数据。
- interface.md 与代码：统一 `Result<T>`（code/message/data/requestId/timestamp），业务码 0/40001/40004/40901/40902/42900/50000。
- 项目无用户表，userId 由调用方透传（`@RequestParam`），幂等/排行榜都用它。
- **鉴权（R3 审计整改后）**：`/api/admin/**` 需 `X-Admin-Token`；`execute`/`check`/`users/{id}/orders` 需 `X-User-Token` 且服务端按令牌解出绑定 userId，不再信任裸传 userId（R3「令牌强绑定 uid」语义，勿回退）；令牌源 = 静态白名单（.env `USER_TOKENS`）∪ 动态注册表（`POST /api/auth/register`，占用返 40903，内存态重启即失）。
- 密钥体系：`application.yaml` 全部 `${ENV:默认}` 绑定（`ADMIN_TOKEN`/`USER_TOKENS`/`REDIS_PASSWORD`/`AI_LLM_API_KEY`/`OSS_*`），`.env` 由 spring-dotenv 读取（后端进程 cwd），密钥不入 Git；前端 `.env` 为构建期（`VITE_ADMIN_TOKEN`/`VITE_USER_TOKEN`）。
- Redis 序列化 DefaultTyping 用 **BasicPolymorphicTypeValidator 白名单**（S2，勿回退 LaissezFaire）。
- 审计/交付文档分工：`audit-report.md` = 2026-09-08 R/C/E 全量审计版（前端 F1~F9 含于第四节）；`final-review.md` = S1/S2/M1~M10 交付审查底稿；进度权威源始终是 `docs/plan.md`。

## 进度快照（2026-09-09 复核更新）

- **4 阶段全部完成**：第 1 阶段 Day1~7 ✅ → 第 2 阶段（Lua 扣减/execute/防重，28 用例）✅ → 第 3 阶段（限流/Stream/可靠性/锁，40 用例）✅ → 第 4 阶段（榜单/指标/前端看板/图片存储/AI 客服/JMeter 5000 压测，59 用例）✅；审计整改 R1~R5/C1~C6（`49a0b09`，62 用例）+ E1~E6 卡（`64311d4`/`11fd6a2`，73 用例）+ 多用户令牌账号 F/G 卡 + S2/M1~M10 交付收尾（`c1f689e`）+ Day 6 学习收口（`7e3d92c`，redis-summary/notework/plan 收尾）+ 审查底稿留档（`97e5bd4`）。
- **HEAD=`97e5bd4`**（main，领先 origin 3 个文档提交未推送）；工作区 135 文件「修改」实为 **CRLF 换行噪音**（`git diff --ignore-cr-at-eol` 为空），无内容差异，提交前须处理 renormalize/autocrlf。
- 回归实证：`target/surefire-reports`（09-09 17:20）全量 **92 用例 / 0 Failures / 0 Errors**，与 plan.md/CLAUDE.md 的 92 用例 BUILD SUCCESS 一致；本 Linux 环境无 JDK，重跑全量需回开发机（先停 dev 后端，防 auto-poll 抢消费）。
- **已核验遗留（audit F 系列前端项，plan/final-review 未逐条登记）**：F1/F4/F8 ✅；**F2/F3/F5/F6 ✅（2026-09-09 已实现并提交，vite build 通过，panda 终审仍可随时提出修订；见 plan.md 当日卡与本日日志）**；F7 半（顶栏账号化已解"演示用户 #1001"，改 productId 后保存仍依赖手动重载）、F9 低（汉堡按钮/🎤📎 死按钮/STATUS_TEXT 未用导出仍在）；E6「合法图片误拒」复现仍挂起（需失败样本）；O1~O10 为 final-review 开放建议。
- 过时引用小项：plan.md 路线图 L40 仍写「HEAD c035ca8」（应 97e5bd4）；CLAUDE.md 描述与现状一致（4 阶段完成 + 92 用例）。
