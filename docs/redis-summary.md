# Redis 实战总结（轻量级电商秒杀系统）

> 学习交付物 · Day 6 复盘总结
> 日期：2026-09-09 ｜ 工程：`E-commerceFlashSaleSystem`（Spring Boot 3.3.4 / Redis 7 + Redisson 3.34 / MySQL 8 + MyBatis-Plus 3.5.7）
> 本文把「秒杀」这个高并发场景拆成**六类企业级问题**，逐一对应 Redis 模式 → 本项目代码位置 → 关键设计 → 踩过的坑 → 验证方式，是学习收口的索引文档。
> 阅读入口：进程链路见 §1；六大问题见 §2~§7；可靠性/幂等横切见 §8；交付加固（鉴权/反序列化白名单）见 §9。

---

## 1. 秒杀主链路（代码视角）

一次 `/api/seckill/execute` 请求经过的每一站都对应一种 Redis 模式：

```
用户请求
  │ ① 滑动窗口限流      rate_limit.lua（ZSet，活动×用户双维）
  │ ② 活动实时校验       SeckillCacheService 活动 Hash（时间窗动态判定）
  ▼
  │ ③ Lua 原子秒杀       seckill_execute.lua：SETNX 令牌 → 检查库存 → DECRBY → 失败自动回滚
  │                      （防重 40901 / 售罄 40902 都在这一个脚本内原子完成）
  ▼
  │ ④ 成功 → 写 Stream  SeckillOrderStreamProducer#sendMessage（削峰，返回 QUEUED + orderNo）
  ▼
自动消费调度            StreamConsumerScheduler（新消息 150ms / PEL 重试 5s）
  ▼
  │ ⑤ 消费者组落库       SeckillOrderConsumer#handle：DB 双唯一键兜底幂等 → XACK
  ▼
  │ ⑥ 成功即入榜        SeckillRankServiceImpl#recordSuccess（ZADD NX，score=抢到时刻）
  ▼
榜单/指标可见性          RankController /api/rank/top10 · AdminMetricsController metrics/snapshot
```

代码位置：`service/impl/SeckillServiceImpl#execute`（`src/main/java/com/ghb/ecommerceflashsalesystem/service/impl/SeckillServiceImpl.java`）；消费者 `stream/consumer/SeckillOrderConsumer.java`；自动调度开关见 `application.yaml` `flash.stream.auto-poll`。

**架构要点**：`execute` 只消耗 Redis（毫秒级），订单由消费者**串行**落库 → 数据库无瞬时冲击，这就是「削峰填谷」。

---

## 2. 高并发读：缓存旁路 + 穿透/击穿/雪崩三防护

**企业问题**：商品/活动详情高频读，全部打 DB 会打爆。

**Redis 方案**：Cache-Aside（旁路缓存）——先查缓存，未命中查库回写，写操作删缓存。

**本项目落地（三大问题逐一解决）**：

| 子问题 | 做法 | 代码位置 |
|---|---|---|
| 缓存雪崩（大量键同时过期） | 过期时间 = TTL + random(0~60)s 抖动 | `CacheKeyConstant.PRODUCT_DETAIL_CACHE_TTL`(300s) + `PRODUCT_DETAIL_CACHE_TTL_RANDOM_RANGE`(60)；写缓存 `service/cache/ProductCacheService#setProductToCache` |
| 缓存穿透（查不存在的 id 一直打库） | 查库未命中写**空值标记**（`""`，TTL 60s），命中标记直接返回 40004 | `PRODUCT_DETAIL_CACHE_EMPTY_TTL`；读三态区分见 `ProductCacheService#getProductFromCache` |
| 缓存击穿（热点 key 重建风暴） | Redisson 互斥锁 + **锁内双重检查**（拿到锁先重查缓存，命中的线程直接返回） | `service/impl/ProductServiceImpl#getProductDetail`：`tryLock(wait, lease)` → 查库回写 → finally 释放（`isHeldByCurrentThread` 判断） |

**踩过的坑**：
- 空值标记是 `String ""`，不能走「读到非 Product 类型就删除」的旧防御分支，否则空值一读即删、穿透防护失效；也不能强转 `(Product)`（ClassCastException）。
- 锁键必须收敛进 `CacheKeyConstant`，别在 Service 里硬编码前缀。

**验证**：`integration/ProductCacheProtectionTest`（穿透：独立 ID 连查 100 次 `verify(times(1))` 只打 1 次库 + 空值标记断言；击穿：300ms 慢查询 + 8 线程 CountDownLatch，锁释放 + 缓存回写）。

---

## 3. 秒杀库存超卖：Lua 原子扣减

**企业问题**：库存扣减是「读-判断-写」三步，纯 Java 并发下会超卖（同秒多扣）。

**Redis 方案**：把「判断库存 > 0 再扣」放进一个 **Lua 脚本**，Redis 单线程执行脚本天然原子，杜绝中间态。

**本项目落地**：
- `src/main/resources/lua/decr_stock.lua`：库存键不存在 / 无法转数字 / ≤0 返回 `-1`，否则 `DECRBY` 返回剩余库存。
- `src/main/resources/lua/seckill_execute.lua`：把「建令牌 → 检查库存 → 扣减 → 失败回滚」合并为一个脚本（§8 幂等详述），契约：`KEYS[1]=库存`、`KEYS[2]=令牌`，返回 `>=0` 剩余 / `-1` 售罄（脚本内已 DEL 令牌）/ `-2` 重复秒杀。
- 脚本注册为 `RedisScript<Long>` Bean：`config/RedisConfig` 的 `decrStockScript` / `seckillExecuteScript` / `rateLimitScript`。

**关键设计**：
- 库存键是独立 String（`seckill:stock:{activityId}`），与活动 Hash 键前缀完全不同——拼错前缀对 Hash 执行 `GET` 会报 `WRONGTYPE` 500。
- 脚本 ARGV 传 `Integer/Long` **数值**，不传 `String.valueOf(...)`——字符串经 JSON 序列化器编码会带引号（`"1800"`），Lua `tonumber` 得 nil。

**踩过的坑**：
- 并发防超卖测试要「请求量 > 库存」才有意义（200 并发抢 100，成功必须恰 =100、拒绝恰 =100、库存归 0）。请求量 == 库存会掩盖缺陷。
- 脚本返回值每个码都要映射业务分支，`-1/-2` 落进兜底 `else` 会错变 `SYSTEM_ERROR`。

**验证**：`LuaStockDeductionTest`（脚本级直测）、`SeckillExecuteScriptTest`（三返回码）、`SeckillExecuteConcurrencyTest`（200 并发防超卖）、JMeter 5000 并发库存精确归 0（`docs/load-test-report.md`）。

---

## 4. 一人多抢/重复下单：SETNX 令牌 → Lua 整合 → DB 唯一键（三道闸）

**企业问题**：同一用户一场秒杀只能成功 1 单；「建令牌」与「扣库存」若分步，并发下会漏判。

**Redis 方案**：先 `SET NX EX` 占幂等令牌（`seckill:user:{activityId}:{userId}`，TTL 30min），占了才放行；令牌存在即「重复秒杀」。

**本项目落地**：令牌逻辑并入 `seckill_execute.lua` 原子完成（见 §3），**售罄时脚本内回滚令牌**——「没抢到不锁死 30 分钟」。DB 侧还有最后一道兜底闸：`seckill_order` 唯一键 `uk_activity_user`（同活动同用户唯一单）。

**踩过的坑**：
- 项目里三类键前缀极易混：活动 Hash `seckill:activity:` / 库存 String `seckill:stock:` / 用户令牌 `seckill:user:`——拼键前先对 `CacheKeyConstant`，与预热写入方保持一致。
- SETNX 的原子性用并发实证：同用户 50 线程并发 execute，恰好 1 成功 + 49 个 `40901`。

**验证**：`SeckillUserDedupTest`（防重 / 售罄回滚令牌不残留 / 同用户 50 并发仅 1 成功）、`SeckillExecuteScriptTest` 的 `-2` 分支。

---

## 5. 瞬时流量冲击：Redis Stream 削峰 + 消费者组异步落单

**企业问题**：秒杀瞬时请求上万，直接写库会被打垮。

**Redis 方案**：execute 校验扣减成功后，不直接落库，而是 `XADD` 写 `seckill:order:stream`，消费者组 `XREADGROUP` 拉取并匀速落库 → 削峰。

**本项目落地**：
- 生产者 `stream/producer/SeckillOrderStreamProducer#sendMessage`：消息体带**成交价快照 + requestTime**（防改价、榜单按业务时刻计分）。⚠️ 用**明文 Map** 投递，不用 `ObjectRecord`——bean 字段经序列化器再入 Stream 会变成 Base64 字符串。
- 消费者 `stream/consumer/SeckillOrderConsumer`：`ensureGroup()`（`XGROUP CREATE ... MKSTREAM` 幂等）→ `consumePending()`（读 `>` 新消息）→ `handle()` 落库 → `XACK`；`consumerRetry()` 读 PEL 重试。
- 自动调度 `stream/scheduler/StreamConsumerScheduler`（`@EnableScheduling`，新消息 150ms / PEL 5s），开关 `flash.stream.auto-poll`。
- 削峰顺序**不可反**：必须先扣库存成功再 XADD（消息 = 已拥有库存的凭证）；XADD 失败反向补偿（回补库存 + 删令牌，审计整改 R2）。

**踩过的坑**：
- `BUSYGROUP` 真实原因在 cause 链里（lettuce 包成 `RedisSystemException`），只查 `e.getMessage()` 会漏判；建组必须幂等。
- 跑全量测试前必须先停 dev 后端：`auto-poll=true` 的 dev 进程会和集成测试抢 Stream 消息，异步用例「偶发少 1 条」实为两进程竞态。
- `my-redis` 容器无持久化，`docker restart` 即丢数据（含 Stream 组）——建组幂等是硬要求。

**验证**：`SeckillPublishStreamTest`（成功才发消息 / 重复 / 售罄不产生消息）、`SeckillConsumerIntegrationTest`（真实 DB 落库 + 重复消费不重复落单）、JMeter 5000 请求 DB 无瞬时冲击。

---

## 6. 接口防刷：ZSet 滑动窗口限流

**企业问题**：单用户高频请求刷爆秒杀接口；固定窗口计数器在窗口边界会突刺。

**Redis 方案**：ZSet 存请求时间戳，`ZREMRANGEBYSCORE` 清窗口外成员 → `ZCARD` 计数 → 未达阈值 `ZADD` 放行。滑动窗口按精确时间过滤，边界平滑。

**本项目落地**：`src/main/resources/lua/rate_limit.lua`，接在 `execute` **第 0 步**（早于活动校验/幂等，超限返回 `RATE_LIMITED(42900)`）。键 `rate:limit:{activityId}:{userId}` **活动×用户双维**（审计整改 E4，多活动连点不再互伤）；窗口/阈值 yaml 化 `flash.rate-limit.window-seconds`(60) / `max-count`(5)。

**关键设计**：
- ZSet member 必须唯一（时间戳 + 随机数）——同一秒内相同 member 会被 `ZADD` 覆盖导致计数丢失。
- 放行时刷新 `EXPIRE`，活跃用户键不提前淘汰。

**踩过的坑**：
- 限流在幂等之前：同用户高频会先被 `42900` 拦而非 `40901`；并发防重用例的并发度必须 ≤ 限流阈值，否则被限流「截胡」。
- 限流键 TTL 60s，凡调 execute 的测试类 setUp/tearDown 都要清 `rate:limit:*`，残留成员会跨用例污染。

**验证**：`RateLimitTest`（脚本级放行/拒绝前 5 后拒、真实滑窗清理、service 级限流最先、不同用户隔离）。

---

## 7. 实时排行榜：Sorted Set

**企业问题**：秒杀成功后实时展示「谁先抢到」的前十名。

**Redis 方案**：ZSet，member=userId，score=抢单成功时刻，`ZREVRANGE`/`ZRANGE` 取 Top N。

**本项目落地**：`service/impl/SeckillRankServiceImpl#recordSuccess` 用 **ZADD NX**（`addIfAbsent`）入榜，score=消息携带的 `requestTime`（抢单成功时刻，越早越靠前）；`topN` 升序取榜 + 回查 DB 回填 orderNo。`GET /api/rank/top10?activityId=`（`RankController`）。

**关键设计（恰到一次的难点）**：
- 「落库」与「上榜」不同事务，PEL 重放 / 幂等命中 / 终态补录会反复触发——**ZADD NX 天然幂等**化解恰好一次（member 已存在不覆盖不重复）。若用 `ZINCRBY` 计数则必须严格只在「首次 insert」调用。
- score 绝不能取「消费者处理时刻」：同一消费批次多条消息落在同一毫秒，会把抢单先后序打乱。业务发生时刻 `requestTime` 早在入队时就随消息下发。

**验证**：`SeckillRankTest`（score 严格递增 / 幂等重放不重分 / 10 用户并发恰 10 人无重复 / 空榜返回空）。

---

## 8. 横切设计：幂等、可靠性、分布式锁、运行指标

### 8.1 幂等四道闸（任一失效都有下一道兜住）
1. **Redis 令牌**：`seckill_execute.lua` 的 `SET NX EX`（防同一用户重复抢）。
2. **DB 唯一键**：`uk_activity_user`（同活动同用户唯一单，`DuplicateKeyException` 兜底视为已处理）。
3. **Stream 消费者组 + XACK**：消息 ACK 前进 PEL，崩溃可重读；`XACK` 才移除。
4. **订单号唯一键**：`uk_order_no`（`OrderNoGenerator` 单时钟源 + 单毫秒 9999 上限，修复过双时钟源碰撞丢单）。

### 8.2 消费可靠性（失败重试 / 死信 / 消息日志）
- 成功置 `seckill_message_log` 为 `SUCCESS` 并落单、ACK；失败计数 +1 留 PEL（未达阈值可重试）；达 `MESSAGE_MAX_RETRY`(3) 转死信流 `seckill:order:dead:stream` + 日志 `DEAD` + ACK。
- 进死信且订单未落库时**补偿库存与令牌**（审计整改 R5）；`service/seckill/DeadLetterReplayService#replay` 提供人工回放（`POST /api/admin/seckill/dead-letters/replay`）。
- 失败原因入库截断 1023 字符（字段 VARCHAR(1024)）。

### 8.3 分布式锁（写入口串行化）
- 适用**多步读改写**写入口：`SeckillCacheService#preheatActivity`（预热：查状态→写缓存→更新 DB，8 线程并发恰 1 成功 + 锁内双重检查防重复预热）、`#resetStock`（库存重置，`AdminSeckillResetStockTest`：活动进行中拒绝重置防复活库存）。
- **execute 主链不加重锁**——防重+扣减已由 Lua 单脚本原子完成，加锁反而降并发。选型先看操作是否「单命令可原子」。
- `tryLock(wait, lease)` + finally 释放 + `isHeldByCurrentThread` 判断 + 恢复中断位。

### 8.4 运行指标（HINCRBY 计数 + 快照落库）
- `seckill:metric:{activityId}` Hash 三字段 `rateLimitReject/duplicateReject/soldOutReject`（`CacheKeyConstant.METRIC_FIELD_*`），execute 三类拒绝抛异常前 `HINCRBY 1`（try/catch 容错仅 warn，不阻断主链）。
- `MetricsServiceImpl#collectMetrics` 聚合（积压 ≈ `XLEN − 该活动落库订单数`，注明多活动共享单键会低估他活动积压的口径局限）；`#captureSnapshot` 落 `seckill_activity_snapshot`。

---

## 9. 交付加固（非功能，但决定能不能安全交付）

| 加固点 | 落地 | 代码位置 |
|---|---|---|
| R3 鉴权 | `/api/admin/**` 需 `X-Admin-Token`；`execute`/`check`/`users/{id}/orders` 需 `X-User-Token`（令牌绑定 userId，不信任裸传） | `config/ApiAccessInterceptor` + `ApiAccessWebConfig` + `SecurityProperties`；`.env` `ADMIN_TOKEN`/`USER_TOKENS` |
| 多用户动态令牌 | 静态白名单 ∪ 动态注册 `UserTokenRegistry`（`ConcurrentHashMap` 双向索引），`POST /api/auth/register` 领随机令牌，占用返 `40903` | `config/UserTokenRegistry`、`controller/auth/UserAuthController` |
| S2 反序列化白名单 | Redis `@class` 反序列化从 `LaissezFaireSubTypeValidator`（放任一切类型）改 `BasicPolymorphicTypeValidator`：仅放行实体包 + 常用 JDK 类型 | `config/RedisConfig`（`RedisTemplate` 序列化器） |
| 密钥可配不落库 | MySQL/Redis/AI/OSS 口令全部 `${ENV:默认}`，由 `.env`（spring-dotenv）注入；OSS 缺密钥不发网络请求并返回可读错误 | `application.yaml` + `.env.example`；`.env` 被 git 忽略 |
| 超长 ID 透传 | 后端 ID 字段输出字符串（`@JsonSerialize(ToStringSerializer)` 思路落地），前端全程字符串透传（去 `Number()` 回环，JS 安全整数 2^53 兜底） | 商品/活动/榜单 VO/DTO 与前端 `store.js` |

---

## 10. 验证证据链（学完对照自查）

| 关注点 | 集成/单元测试 | 压测/报告 |
|---|---|---|
| 缓存三防护 | `ProductCacheProtectionTest`、`Phase1IntegrationTest` | - |
| 原子扣减 / 防超卖 | `LuaStockDeductionTest`、`SeckillExecuteScriptTest`、`SeckillExecuteConcurrencyTest` | `docs/load-test-report.md`：5000 并发库存归 0、订单=1000=库存、无超卖 |
| 幂等防重 | `SeckillUserDedupTest`、`AuthRegisterIntegrationTest` | 消息日志 1000/1000 无重复 |
| 限流 | `RateLimitTest` | soldOutReject=4000、总量守恒 5000 |
| Stream 削峰/可靠性 | `SeckillPublishStreamTest`、`SeckillConsumerIntegrationTest`、`SeckillMessageReliabilityTest` | DB 无瞬时冲击 |
| 排行榜 | `SeckillRankTest` | 前端看板榜单刷新 |
| 指标/快照 | `SeckillMetricsTest` | metrics 面板 |
| 预热/库存重置锁 | `PreheatConcurrencyTest`、`AdminSeckillResetStockTest` | - |

全量回归：**92 用例 / 0 Failures / 0 Errors / BUILD SUCCESS**（2026-09-09）。测试运行约定：全量前先停 dev 后端（`auto-poll` 竞态）；用 Surefire 汇总计数，勿用 `grep @Test`（注释里有两处会虚高）。

---

## 附：本文与其它文档的关系
- 规划与逐日进度、全部经验教训：`docs/plan.md`（唯一进度源）
- 审计整改清单：`docs/audit-report.md`（R1~R5 / C1~C6 / E1~E6）
- 压测报告：`docs/load-test-report.md`；前端存储双策略：`docs/storage.md`
- 接口契约（含 4.15 注册、鉴权头）：`docs/interface.md`；数据表与唯一键：`docs/database.md`
- 学习笔记（概念向、自己的话）：`docs/notework.md`
