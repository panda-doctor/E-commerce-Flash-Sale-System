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

## 进度快照（2026-09-02 核对）

- 第 1 阶段 Day 1~7 全部 ✅ 并已提交（HEAD=3787598「完成 Day 7」）。
- 第 2 阶段（秒杀核心与原子库存扣减）🔄 进行中，已写 Day 1 拆分计划。当前工作区未提交：`decr_stock.lua`、`RedisConfig.decrStockScript` Bean、`LuaStockDeductionTest.java`（**半成品，逻辑有误未完成，见当日日志**）、`docs/plan.md` 更新、`docs/day.md` 已暂存删除。
- 待办不一致（审查发现）：CLAUDE.md 仍写「Phase 1 Day 3」已过时；plan.md/CLAUDE.md 多处引用已删除的 docs/day.md；plan.md 声称的 phase1-review.md 验收报告文件实际不存在。
