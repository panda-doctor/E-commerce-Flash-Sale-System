# 面试高频问题 · 配套答案（结合本项目实现）

> 配套问卷：`docs/interview-questions.md`（编号一一对应）。
> 所有答案均以本仓库真实代码/数据为准，可直接对照 `docs/plan.md`、`docs/redis-summary.md`、`docs/load-test-report.md` 复核。
> 数字口径：自动化用例 92（E6 收尾批后 98，待开发机回归）；压测 5000 请求；审计闭环 38 项（R1~R5/C1~C6/E1~E6/F1~F9/S1~S2/M1~M10）。

---

## A. 项目与架构

### A1 ⭐ 项目介绍（背景/目标/架构）
**答**：这是一个**教学向但工程完整**的电商秒杀系统。背景是"用最小业务面把 Redis 的企业级用法打穿"——目标不是做商城，而是把**高并发读、防超卖、防重复、流量削峰、接口防刷、实时榜单**六类问题各用一个 Redis 模式落地并验证。
架构是单体分层：`Controller（统一 Result + 令牌拦截）→ Service（业务编排）→ Redis（缓存/库存/令牌/限流/Stream/榜单/指标）+ MySQL（5 张表终态）`；前端是独立的 Vue3 SPA（活动广场/详情/管理台/AI 客服）。
**事实/代码**：`docs/project.md` 定义目标；`docs/redis-summary.md` 是"问题→模式→代码位置"索引；端口 8081、Redis db1。
**追问：为什么不上 Kafka？** 教学场景要"一站一模式"，Redis Stream 与缓存/库存同源，部署成本为零；削峰语义（消费组/PEL/ACK）已经完整。生产上量级上来应换 Kafka/RocketMQ（见 K1）。
**陷阱**：别说"因为 Redis 快"——要说清削峰+可靠性语义，且承认取舍。

### A2 ⭐ 秒杀主链执行顺序
**答**（严格按代码顺序，每步都有理由）：
1. **滑动窗口限流**（`rate_limit.lua`，键 `rate:limit:{activityId}:{userId}`）——最前置，用最便宜的原子操作挡掉刷子，避免无效请求打到后续环节；
2. **活动缓存查询**（Hash `seckill:activity:{id}`，未命中回源 DB 并按实时时间窗细分拒绝原因）；
3. **时间窗/状态校验**（未开始/已结束/已取消拦截，售罄交给 Lua 兜底）；
4. **整合 Lua**（`seckill_execute.lua`）：建幂等令牌 + 校验库存 + 原子扣减 + 售罄回滚；
5. **指标埋点**（三类拒绝 `HINCRBY`）；
6. **生成订单号 → XADD** 到 `seckill:order:stream`（消息含成交价快照、`requestTime`）；
7. **入队失败立即反向补偿**（回补库存 + 删令牌）后重抛；
8. 返回 `QUEUED + orderNo`；后续由消费者异步落库、入榜。
**为什么限流最前**：限流是 O(logN) 的 ZSet 操作、无网络回源，放在最前能以最低成本保护 DB/Redis 重活；顺序颠倒会导致"被限流的请求也消耗了库存/令牌"。
**陷阱**：把"查活动"说成直接查库——本项目是缓存优先，主链不打库是设计红线。

### A3 模块划分与键收敛
**答**：按"横切/领域/角色"三层划分：`common`（Result/异常/常量/工具）、`config`（Redis/Redisson/MyBatis/鉴权/存储映射）、`domain`（entity/dto/enums/vo）、`mapper`、`service`（cache/seckill/rank/metrics/ai/storage/stream/impl…）、`stream`（producer/consumer/scheduler）、`controller`（admin/auth/product/rank/seckill/support/health）。
所有键前缀收敛到 `common/constant/CacheKeyConstant`，好处：①拼键不再手写字符串，避免"库存键误拼活动前缀导致 WRONGTYPE"这类事故（项目里真踩过 3 次）；②TTL/阈值/组名等常量集中，改一处全生效；③便于文档与代码对账。
**加分**：可以顺势说"配置项（限流窗口/阈值）用 yaml 参数化，常量只作默认值"。

### A4 Redis 数据结构与场景
**答**（对应键表）：
| 结构 | 键 | 场景 |
|---|---|---|
| String | `product:detail:{id}`、`seckill:stock:{id}`、`seckill:user:{aid}:{uid}` | 缓存旁路、库存原子扣减、幂等令牌 |
| Hash | `seckill:activity:{id}`、`seckill:metric:{id}` | 活动信息+时间窗、三类拒绝计数 |
| ZSet | `rate:limit:{aid}:{uid}`、`seckill:rank:{aid}` | 滑动窗口限流、实时手速榜 |
| Stream | `seckill:order:stream`、`seckill:order:dead:stream` | 下单削峰、死信 |
| 分布式锁（Redisson） | `seckill:lock:preheat:{id}`、`seckill:lock:reset:{id}`、商品回源锁 | 写入口串行化、缓存击穿互斥 |
**加分**：顺带说明每个键的 TTL 设计（缓存 300+random(60)、空值 60、令牌 1800、限流 window+1）。

### A5 "落库才算抢到"如何落地
**答**：三层含义：
① **execute 成功 ≠ 抢到**，只代表"拿到排队资格 + 库存已预占"（返回 QUEUED）；
② **是否入榜、是否算成交，以 `seckill_order` 落库为准**——榜单在消费者"最终成功"路径统一 `recordSuccess`（ZADD NX）；
③ **失败必须回滚**：Lua 内售罄回滚令牌、XADD 失败服务层反向补偿（INCR+DEL）、进死信且订单未落库时补偿库存/令牌。由此保证"Redis 上的资格"与"DB 终态"最终一致。
**代码**：`SeckillServiceImpl#execute`、`SeckillOrderConsumer#handle`、`DeadLetterReplayService`。

### A6 最有技术含量的部分
**答**（任选一条主线讲深）：
- **可靠性设计**：Redis Stream 消费组 + PEL 重试 + 死信 + 补偿 + 人工回放，构成"消息不丢、不重复、可兜底"的完整闭环（对应 R5 审计整改）；
- **幂等四道闸**：Lua 内 SETNX → DB `uk_activity_user` → `selectByOrderNo` 查重 → XACK/PEL 语义；
- **一致性补偿的三条失败路径**：售罄回滚、发消息失败回补、死信补单。
**建议**：选"可靠性闭环"讲，因为它能把 Stream、DB 唯一键、幂等、补偿、可观测（消息日志三态）串成一条线。

### A7 重做会改什么
**答**：诚实列举（正好对应 final-review 开放项）：
① 分布式 ID 与订单号改**号段/雪花带 workerId**，支持多实例；
② 榜单/指标/Stream 键加**生命周期治理**（活动维度清理或 TTL），避免内存单边增长；
③ OSSClient **单例复用**（现在每次 new/shutdown）；
④ AI 客服补**限流与配额**、UserTokenRegistry 加**上限与过期清理**；
⑤ 引入 **Testcontainers/embedded Redis** 替代"必须本机起 MySQL+Redis"的集成测试前置；
⑥ 消息队列抽象出接口，便于将来替换 Kafka。
**陷阱**：回答"没有要改的"是减分项，面试官要听的是自我批判能力。

---

## B. 缓存与一致性

### B1 ⭐ 缓存三兄弟与解决方案
**答**：
- **穿透**（查不存在的数据，缓存永远不命中，请求全打 DB）：对不存在商品写**空值标记 `""`（TTL 60s）**，二次查询命中标记直接返回 40004 不再打库；测试用独立 ID（999999）连续查 100 次，`verify(times(1))` 证明只打库 1 次。
- **击穿**（热点 key 过期瞬间，大量并发同时回源）：**Redisson 互斥锁 + 锁内双重检查**，只有 1 个线程查库回写，其余等待后命中缓存；8 线程并发测试验证只查库 1 次。
- **雪崩**（大批 key 同时过期/Redis 宕机）：**TTL 加随机抖动**（300 + random(60) 秒）打散过期时间；另用缓存优先 + DB 兜底 + 限流保护。
**代码**：`service/cache/ProductCacheService`、`service/impl/ProductServiceImpl`、`ProductCacheProtectionTest`。

### B2 TTL 为什么加随机值
**答**：同批写入的 key 若 TTL 完全相同，会在同一秒集中失效，瞬时流量全打到 DB = 自己制造缓存雪崩。TTL = 基础值 + 随机抖动可把失效点摊开。
本项目：`PRODUCT_DETAIL_CACHE_TTL=300s` + `random(60)s`（`ThreadLocalRandom`）；空值标记固定 60s；活动缓存为 `TTL + SECKILL_CACHE_TTL_EXTRA(60s)`。
**加分**：说明"抖动范围要远小于基础值，否则缓存命中率下降"，60/300 是 20% 的折中。

### B3 ⭐ 空值缓存与三态读取
**答**：用 `""` 而不是 `null`，因为 Redis 存 null 会退化成"键不存在"，无法与真正的未命中区分。读取必须区分三态：
① **未命中**（key 不存在）→ 需要回源；
② **命中空值标记**（`""`）→ 数据确定不存在，直接返回 40004，**不再回源、也不删除标记**；
③ **命中真实值** → 反序列化返回。
项目踩坑：早期实现"非 Product 类型一律删除"，导致空值标记一读就被删，穿透保护形同虚设；空值分支还禁止强转 `(Product)`（会 ClassCastException）。
**代码**：`ProductCacheService#getProductFromCache`、`ProductServiceImpl#getProductDetail`。

### B4 击穿防护的锁
**答**：为什么用锁：热点 key 失效瞬间要保证"只有一个线程回源"，互斥锁最直接（逻辑过期方案需要额外线程异步重建，实现更复杂，本项目选了教学价值更高的锁）。
参数：`tryLock(waitTime=2s, leaseTime=30s)`；wait 防无限等待，lease 防持锁进程崩溃导致死锁。锁内**双重检查**（等锁期间别人可能已回写）；`finally` 中用 `isHeldByCurrentThread()` 判断再 `unlock()`，防误释放他人锁；`InterruptedException` 恢复中断位。
**追问：为什么不加在看活动/扣库存上？** 因为那是单命令/Lua 原子操作，加锁只降并发（见 E1）。

### B5 ⭐ 缓存与 DB 一致性
**答**：采用 **Cache Aside + 更新数据库后删除缓存**（不是更新缓存）。理由：
① 更新缓存有并发写覆盖问题（两个线程先写 DB 后写缓存，顺序错乱导致缓存存旧值）；
② 删除缓存是幂等操作，失败可重试；
③ 本项目读多写少（商品/活动配置），删除带来的"一次回源"成本可接受。
**延迟双删要不要**：本质是"删缓存与主从复制延迟/并发读回填脏数据"的补丁；本项目是单库直连、写后删除+回源最新值，暂不需要；若上主从/读写分离，需评估"先删 → 写库 → 延迟再删"或订阅 binlog 失效。
**项目增强**：删除缓存同时把 `preheat_status` 回置未预热（R4），避免"新配置 + 旧缓存"混合态。

### B6 更新后缓存不失效的后果与修复
**答**：后果是**旧价旧库存落单**——详情页与 execute 都命中旧缓存，用户按旧价抢购，订单金额与活动配置不一致；且管理员以为改了配置，实际线上没生效。
修复（审计 R4）：活动更新成功后 ① 删除 `seckill:activity:{id}` 与 `seckill:stock:{id}`；② `preheat_status` 置 0 要求重新预热；③ 用 `@Version` 乐观锁防并发更新丢写；④ 缓存失效失败不回滚 DB（记日志 + 预热幂等兜底）。
**陷阱**：把"没清缓存"说成小问题——它直接破坏资金一致性。

### B7 热 key / 大 key
**答**：本项目现状：`seckill:metric:*` 是小 Hash（3 个计数），库存/令牌是单值，**没有典型大 key**；热 key 是"单个热门活动的库存键/流键"——所有请求都打同一个 key。
治理思路（按代价排序）：① 本地缓存 + 广播失效（活动配置读多写少）；② 库存**分片**（`stock:{id}:{shard}` 求和，需改 Lua 语义）；③ 读写分离/Redis Cluster 按 activityId 打散；④ 监控用 `redis-cli --hotkeys`（LFU）与 `--bigkeys`。
**加分**：承认"教学项目量级没触发，但知道怎么发现和怎么拆"。

---

## C. 防超卖与 Lua

### C1 ⭐ 为什么"先查再扣"不行
**答**：`GET stock` 与 `DECR` 是两条命令，之间可被其他请求穿插（典型 check-then-act 竞态）：两个线程都读到 1，都判断"有库存"，然后各扣一次 → 库存 -1，超卖。
解决方案三选一：① 分布式锁串行化（并发度骤降，不适合秒杀主链）；② `DECR` 后判断返回值 <0 再回补（能防超卖但有中间态、回补可能失败）；③ **Lua 脚本把"判断+扣减"合成一次原子执行**（本项目选择）。
**代码**：`resources/lua/decr_stock.lua`（键不存在/非数字/≤0 返回 -1）、`seckill_execute.lua`。

### C2 ⭐ Lua 原子性原理
**答**：Redis 执行命令是**单线程**的（网络/命令处理主线程串行），Lua 脚本以整段脚本为一次"命令"执行，期间不会被其它客户端命令穿插；脚本内的多个 `GET/SET/DECR/DEL` 因此具备原子性、隔离性。**注意**：原子 ≠ 事务回滚——Lua 中途报错，已执行的写命令不会自动回滚（所以脚本要写得防御性强，不能依赖"报错自动回滚"）。
**追问：Lua 慢会怎样？** 会阻塞整个 Redis 实例（单线程），所以脚本必须短小、禁止循环大集合/复杂计算；`lua-time-limit` 只影响是否允许 `SCRIPT KILL`。本项目三个脚本都是 O(1)~O(logN) 的小脚本。

### C3 ⭐ seckill_execute.lua 契约
**答**：KEYS[1]=库存键、KEYS[2]=幂等令牌键；ARGV[1]=令牌值、ARGV[2]=令牌 TTL（1800s）。逻辑：① `EXISTS token` → 有则返回 **-2**（重复秒杀）；② `SET token value NX EX ttl`（防御性再判，失败返回 -2）；③ `GET stock` 不存在/≤0 → `DEL token` 后返回 **-1**（售罄回滚）；④ `DECRBY stock 1` → 返回 **≥0**（剩余库存）。
Java 侧逐一映射：`≥0 → QUEUED`、`-1 → OUT_OF_STOCK(40902)`、`-2 → DUPLICATE_PURCHASE(40901)`、`null → SYSTEM_ERROR`（每个返回码都必须有分支，早期 `-2` 曾落进兜底变成 50000）。
**追问：为什么先 EXISTS 再 SET NX？** 语义上 `SET NX` 已足够，EXISTS 是为了给"重复秒杀"一个明确的分支位置，同时防御 SET 失败；两者都在同一原子脚本内，不会引入竞态。

### C4 为什么令牌+扣减必须同脚本
**答**：若分步（服务层先 `setIfAbsent` 再执行扣减脚本），中间会出现三类不一致：① 扣减失败但令牌已建 → 用户 30 分钟不能重试（项目早期真踩过，靠"售罄回滚"补丁解决，最后统一进脚本）；② 服务崩溃在两步之间 → 令牌残留、库存悬空；③ 多一次 RTT，性能更差。
合并后"建令牌→校验→扣减→回滚"同生共死，**彻底消除中间态**。

### C5 Lua ARGV 字符串坑
**答**：本项目用 `GenericJackson2JsonRedisSerializer` 序列化脚本参数，传 `String.valueOf(ttl)` 会被序列化成带引号的 `"1800"`，Lua `tonumber` 得到 nil，只能靠脚本 `or 1800` 兜底——**常量一改就静默失效**。修正：脚本参数一律传数值类型（`Integer/Long`），序列化后是裸数字。
**加分**：说明"这类问题的可怕之处是静默降级，不报错"，所以要脚本级测试直测返回码。

### C6 ⭐ 补偿设计
**答**：三条失败路径：
① **售罄/库存键不存在**：Lua 内 `DEL token` 回滚（在脚本内，天然原子）；
② **XADD 失败**（库存已扣、令牌已建）：`catch` 中反向补偿（`INCR` 回补库存 + `DEL` 令牌，按可重入语义实现），补偿失败只记 error 日志不覆盖原异常，交给人工核对/后续补偿任务；
③ **进死信且订单未落库**：按消息字段补偿库存与令牌，并提供 `DeadLetterReplayService` 人工回放（快照 → 重新原子占位 → 重投主 Stream → XDEL）。
**原则**：补偿必须幂等（INCR 一步、DEL 对不存在键返回成功），且**不能吞掉原始异常**。

---

## D. 幂等与防重复

### D1 ⭐ 幂等四道闸
**答**：
① **Lua 内 SETNX 令牌** `seckill:user:{activityId}:{userId}`（TTL 30min）——挡住同用户并发/重复请求，脚本级原子；
② **DB 唯一键 `uk_activity_user(activity_id, user_id)`**——最终兜底，即使 Redis 数据被清/被绕过也不可能重复落单；
③ **消费者 `selectByOrderNo` 查重 + `DuplicateKeyException` 兜底**——防消息重复消费造成重复插入；
④ **XACK/PEL 语义 + 榜单 ZADD NX**——消息层与统计层各自幂等（重放不重复计分）。
**验证**：同用户 50 线程并发 execute，恰好 1 次成功、49 次 40901。

### D2 令牌 TTL 与回滚原因
**答**：TTL=30 分钟（`SECKILL_USER_TOKEN_TTL=1800`）。语义是"排队资格"而非"已购凭证"：① 太长会锁死用户（若消息彻底失败）；② 太短则用户在异步落库完成前重复请求会二次扣库存。30 分钟覆盖"入队→落库→用户轮询"全窗口。
没抢到必须回滚：否则用户"没抢到却被锁 30 分钟"，体验和语义都错；回滚与扣减在同一 Lua 内完成。

### D3 重复秒杀的错误码与前端配合
**答**：返回业务码 **40901 DUPLICATE_PURCHASE**（HTTP 仍是 200，统一 `Result` 包裹，`data.result=DUPLICATED`）。
前端拿到 40901 不会只弹错：会去榜单里找自己已入队的订单并**接着轮询订单状态**（可能上次已成功只是没看到），找不到才提示"你已参与本场"。
**加分**：体现"错误码契约 + 前端闭环"的工程思维，而不是甩一个 toast。

### D4 ⭐ 重复消费会重复下单/入榜吗
**答**：不会。① 下单：`selectByOrderNo` 先查，命中即视为已处理；未命中 insert 时若撞唯一键（`uk_order_no`/`uk_activity_user`）抛 `DuplicateKeyException`，按"已处理"兜底（同时走成功路径补记榜单/日志）；② 入榜：用 **ZADD NX**（`addIfAbsent`），member=userId 已存在则不覆盖不计分——天然幂等；③ 日志：`uk_stream_message(stream_key, stream_message_id)` 保证一条消息一条日志。
**陷阱**：若用 `ZINCRBY` 计数式榜单，重放就会重复加分——这是本项目刻意选 ZADD NX 的原因。

### D5 幂等键的选择
**答**：业务语义是"**一个人在一个活动里只能有一单**"，所以幂等/令牌的自然键是 `activityId + userId`：`seckill:user:{activityId}:{userId}`，与 DB `uk_activity_user` 完全同构，Redis 与 DB 两层校验口径一致。
不用 orderNo：orderNo 在扣减成功后才生成，无法在"扣库存之前"挡住重复请求；它是消息层幂等键（`uk_order_no`），两个键各管一层。

---

## E. 分布式锁

### E1 ⭐ 用锁的地方 & 为什么主链不加锁
**答**：用了三处，都是"**多步读改写**"的写入口：① 活动预热（查状态→写缓存→更新 DB/预热态，锁 `seckill:lock:preheat:{id}`）；② 库存重置 `seckill:lock:reset:{id}`（含"活动进行中拒绝重置"守卫）；③ 商品缓存击穿回源互斥。
**execute 主链不加锁**：防重+扣减已被单条 Lua 原子完成（单键操作），加锁会把并发度从"Redis 单线程原子"降成"分布式锁排队"，纯负优化。
**结论句式**："先看操作能不能用一条命令/一个脚本原子完成，能就不加锁；跨资源、跨步骤的读改写才加锁。"

### E2 ⭐ Redisson 原理与看门狗
**答**：Redisson 的 `RLock` 底层是 **Hash 结构 + Lua 脚本**：`HSET key uuid:threadId 重入次数`，加锁/解锁都是原子脚本，因此**可重入**；同时提供发布订阅唤醒等待者。
**看门狗**：不显式传 `leaseTime` 时，Redisson 默认锁租期 30s 并启动后台定时任务，每 10s（1/3 租期）续期一次，直到 `unlock`；**一旦你显式传了 leaseTime，看门狗不生效**，到点自动释放。
本项目 `tryLock(waitTime, leaseTime)` **显式传了 leaseTime**（2s/5s 等待、30s 持有）：教学场景业务短、可控，且明确"崩了也能自动解锁"；代价是**业务超过 30s 就会锁失效**，需要靠业务耗时约束或改用手动续期（见 E4）。

### E3 双重检查与释放
**答**：① **双重检查**：拿锁前读到的状态可能是旧快照，等锁期间别的线程可能已完成预热/回源，拿到锁必须重新查库，命中就直接返回（否则会重复预热/重复回源）。② **参数**：wait 取业务可接受等待（预热 5s、商品回源 2s），lease 取业务最坏耗时的 2~3 倍（30s）。③ **释放**：`finally` + `isHeldByCurrentThread()`，避免锁超时被别人拿到后自己误删；`InterruptedException` 要 `Thread.currentThread().interrupt()` 恢复中断位后再抛业务异常。
**验证**：`PreheatConcurrencyTest` 8 线程并发预热，恰好 1 成功、7 被拒、`update` 只调 1 次、锁键最终释放。

### E4 业务超时与误删风险
**答**：若业务执行超过 leaseTime：锁自动过期→别人可加锁→原线程 `unlock` 时若无 `isHeldByCurrentThread` 检查，可能删掉**别人的锁**（Redisson 的 Hash 校验 uuid+threadId，本身就不允许删他人锁，但业务侧仍要做幂等保护）。
方案：① 让 lease 覆盖最坏耗时（本项目做法）；② 不传 leaseTime 用看门狗自动续期（适合长任务）；③ 业务侧加"锁归属校验/版本号"，保证即使并发也只生效一次。
**加分**：提一句"锁只是减少并发冲突，**正确性最终仍要幂等+唯一键兜底**"，这是分布式系统的核心心态。

### E5 锁 vs Lua；Redlock
**答**：选择标准："**单键原子**用 Lua/单命令；**多步、跨 key、含外部 IO（DB/HTTP）**的读改写用锁编排。"本项目预热要写 Redis + 改 DB，属于后者。
Redlock 争议（Martin Kleppmann vs antirez）我的看法：Redlock 依赖多节点时钟与网络假设，GC 停顿/时钟漂移下仍可能"两个客户端同时持锁"；与其争论锁的绝对安全，不如接受"**锁 + fencing token/唯一键兜底**"的现实工程路线。本项目单实例 Redisson，不涉及 Redlock。

---

## F. 限流

### F1 ⭐ 滑动窗口 ZSet 实现
**答**：`rate_limit.lua`：① `ZREMRANGEBYSCORE key -inf now-window` 清理窗口外成员；② `ZCARD` 统计窗口内请求数；③ `count >= maxCount` 返回 0（拒绝），否则 `ZADD` 当前请求并 `EXPIRE key window+1` 返回 1（放行）。全部在一个脚本内原子完成。
**为什么不用固定窗口**：固定窗口（如 `INCR key; EXPIRE 60`）在窗口边界会有双倍突刺（第 59 秒 5 次 + 第 61 秒 5 次 = 1 秒内 10 次），滑动窗口按精确时间范围过滤，边界平滑。
**对比令牌桶/漏桶**：滑窗限制"任意窗口内次数"，令牌桶允许突发但有稳态速率——秒杀防刷用滑窗更贴合"每分钟最多 N 次"的语义。

### F2 member 唯一性
**答**：member 用 `时间戳-纳秒/随机数`。若只用时间戳：同一秒内的多个请求 member 相同，`ZADD` 会**覆盖**而不是新增，计数丢失 → 限流失效（本该拒绝的请求被放行）。这是脚本注释里专门写明的坑。

### F3 双维限流键
**答**：键是 `rate:limit:{activityId}:{userId}`（E4 审计整改）。单 userId 维度的问题：同一用户快速切换多个活动会互相误伤（抢 A 活动把 B 活动的额度用掉）。
**换 userId 绕过**：单靠 userId 确实可绕过；生产需要叠加 **IP/设备指纹维度**与**风控**（见 K6）；本项目是演示系统，保留了 userId 维度并参数化（`flash.rate-limit.window-seconds/max-count`）。

### F4 配置化与顺序
**答**：窗口/阈值走 yaml `flash.rate-limit.*`（默认 60s/5 次），常量类只作默认值，运行期可用环境变量覆盖。
**顺序不能反**：限流必须在幂等之前。否则同用户高频请求会先消耗令牌/库存，再被限流拒绝——额度浪费且错误码不符合预期（应该 42900，而不是 40901）；测试里也验证过"不存在的活动也先返回 42900 而非 404"，用错误码反证链路顺序。

---

## G. Redis Stream 与异步可靠性

### G1 ⭐ 为什么用 Streams
**答**：秒杀校验通过后**不直接落库**，只把"预占库存的凭证"写进 Stream 立即返回，后台消费者匀速落库——把瞬时流量峰值摊平成 DB 能承受的速率（削峰）。
选 Streams 的原因：① 原生**消费者组 + PEL + XACK**，具备"至少一次"投递与失败重投语义（List 的 BRPOP 没有 ACK，发布订阅不持久化）；② 与缓存/库存同一套 Redis，零额外部署；③ 支持多消费者水平扩展。
**对比 Kafka**：Kafka 吞吐/堆积能力/生态更强，但要独立集群与运维；本项目量级（5000 请求）Streams 足够。上量后换 Kafka（见 K1）。
**代码**：`stream/producer/SeckillOrderStreamProducer`（XADD）、`SeckillOrderConsumer`（XREADGROUP/XACK）、`StreamConsumerScheduler`（调度）。

### G2 ⭐ 消费组 / PEL / XACK
**答**：`XGROUP CREATE stream group MKSTREAM` 建组（BUSYGROUP 幂等处理，注意真实的 BUSYGROUP 藏在异常 cause 链里）。`XREADGROUP GROUP g c COUNT n STREAMS stream >` 读**新消息**；消息被投递给某消费者后进入 **PEL（Pending Entries List）**，直到 `XACK` 才移出。
**"消费完成"的定义 = 业务副作用成功 + XACK**：只 ACK 不落库是最危险的 bug（项目 Day 4 真踩过：重构时丢了 insert 语句），所以审查时要核对"副作用语句是否还在"。

### G3 ⭐ 重试与 PEL 膨胀
**答**：失败时不 XACK，消息就留在 PEL；重试入口用 `XREADGROUP ... STREAMS stream 0`（读 PEL 里的旧消息）重新投递，这就是"重试机制"。超过 `MESSAGE_MAX_RETRY=3` 次：`XADD` 到死信流 + 日志置 DEAD + `XACK` 原消息（把它移出 PEL）。
**PEL 会不会膨胀**：会——所以必须有"转死信 + ACK"的出口；本项目还专门修了"解析异常只 log 不 ACK → 毒消息每轮重试、PEL 无限增长"的高危问题（审计 R5）：解析失败也走统一失败计数（宽松解析 → handleFailure），到阈值转死信。

### G4 ⭐ 死信与回放
**答**：死信流 `seckill:order:dead:stream`，消息体为**明文 Map**（原字段 + 错误信息）。进死信时若订单未落库，补偿库存与令牌（避免"库存悬空 + 用户不可重试"）。
人工回放 `DeadLetterReplayService`：按 `activityId + orderNo` 定位死信 → **快照死信内容** → 重新原子占位（Lua 建令牌扣库存）→ 重投主 Stream → `XDEL` 死信。接口 `POST /api/admin/seckill/dead-letters/replay`（需 admin 令牌）。
**加分**：回放设计遵循"先占位再投递 + 幂等"，重放不会造成重复订单（DB 唯一键兜底）。

### G5 明文 Map 与 Base64 坑
**答**：生产者若用 `StreamRecords.objectBacked(bean)`，字段会被序列化器编码后入 Stream，消费端读回 `MapRecord` 时字段变成 **Base64 字符串**（`200` → `"MjAw"`），`Long.valueOf` 直接抛 `NumberFormatException`。
修正：**生产者用明文 Map 投递**（字段全为可读字符串），消费端用 `toLong/toStr` 安全转换；死信投递同样用明文 Map。这也是"Stream 消息契约要显式、不要依赖序列化器"的教训。

### G6 宕机与重复消费
**答**：消息在 XACK 前一直在 PEL，消费者进程崩溃重启后可通过"读 PEL（STREAMS stream 0）"重新拿到 → **不会丢**（至少一次语义）。重复消费由幂等四道闸兜住（查重 + 唯一键 + ZADD NX + 消息日志唯一键）。
补充：本项目 `my-redis` 容器演示环境**没开持久化**，`docker restart` 会丢全部 key——所以代码里建组必须幂等（MKSTREAM + BUSYGROUP 判 cause 链），正式部署要开 RDB/AOF。

### G7 消息里带快照与业务时间
**答**：① **成交价快照**（`seckillPrice`）：消费者落库直接用消息里的价格，不再回查活动表，杜绝"活动改价后订单金额不一致"；② **`requestTime`**：榜单 score 用"抢单成功时刻"而不是"消费者处理时刻"——同一批消费会在同一毫秒入榜，用处理时刻会把先后序彻底打乱（测试断言 score 严格递增必 flaky）。
**加分**：这说明"消息体要携带业务发生时间/业务快照"，是异步解耦的通用经验。

### G8 自动消费调度与测试隔离
**答**：`StreamConsumerScheduler`（`@EnableScheduling`）：新消息 `@Scheduled(fixedDelay=150)` 高频拉取保吞吐；PEL 重试 `fixedDelay=5000` 低频重投防风暴。开关 `flash.stream.auto-poll`（默认 true）。
**测试必须关**：集成测试自己手动驱动消费（`consumePending`），若后台调度同时在跑，会**抢走测试写入 Stream 的消息**，表现为"异步类用例随机少 1 条"的假 flaky（实为两进程争抢）。约定：15+ 个集成测试类统一 `@SpringBootTest(properties="flash.stream.auto-poll=false")`，且跑全量回归前先停 IDE 里起的 dev 后端。

---

## H. MySQL / 事务 / 数据

### H1 ⭐ 订单表唯一键
**答**：`uk_order_no(order_no)` 与 `uk_activity_user(activity_id, user_id)`。
① `uk_activity_user`：**业务级防重最终兜底**——即使 Redis 令牌失效/被清、消息重复投递，DB 也绝不允许同一用户在同一活动有第二单；
② `uk_order_no`：**消息级幂等**——同一订单号重复插入直接失败，被消费者识别为"已处理"。
另外 `seckill_message_log` 有 `uk_stream_message(stream_key, stream_message_id)` 保证"一条 Stream 消息一条日志"。

### H2 乐观锁
**答**：`seckill_activity.version` + MyBatis-Plus `@Version`（需 `OptimisticLockerInnerInterceptor` 插件注册）。场景：管理端并发更新同一活动配置（改价/改库存/改时间窗），`UPDATE ... WHERE id=? AND version=?`，更新 0 行说明版本冲突 → 抛业务异常提示"已被他人修改，请刷新重试"。
**取舍**：秒杀库存**不用**乐观锁重试（高冲突场景重试风暴），而是走 Redis Lua；活动配置是低频写、冲突少，乐观锁最合适。悲观锁会长时间持锁且不适合 Web 场景。

### H3 订单状态流转
**答**：枚举 `QUEUING(0, 排队中)`、`CREATED(1, 已创建)`、`CREATE_FAILED(2, 创建失败)`、`CANCELLED(3, 已取消)`。
execute 成功后订单还不存在，查询接口按"未落库 → QUEUING"返回；消费者落库置 CREATED；前端拿到 QUEUING 就进入 900ms 轮询（最多 60 次），拿到 CREATED 刷榜单。
**审计整改点**：早期实现把 FAILED/CANCELLED 也折叠成 QUEUING（前端永远等不到结果），现在按枚举输出 `status + statusDesc`，字段名对齐契约（`createdAt`）。

### H4 订单号并发碰撞
**答**：现象是压测"库存 0 但订单只有 999 单"——`uk_order_no` 把重复订单号的那条插入判成幂等"已处理"，等于**静默吞单**。
根因：`OrderNoGenerator` 内部用了**两个时钟源**（一边 `System.currentTimeMillis()` 取时间部分、一边用另一个来源判定是否同毫秒决定自增序号），两处毫秒错位 → 在某一毫秒边界上序号重置，生成完全相同的订单号。
修复：统一为**单一时钟源**，同毫秒内序号自增，超上限（9999）自旋等待下一毫秒；复测 1000/1000 无重复、订单数=库存。
**教训一句话**："唯一 ID 的时钟判定与文本输出必须同源"，以及"唯一键+幂等兜底会把碰撞静默化成丢单，所以必须有守恒断言（订单数=库存）来兜监控。"

### H5 索引设计
**答**：
- `seckill_activity`：`idx_activity_time_status(start_time,end_time,status)`（列表/时间窗筛选）、`idx_activity_product_id`（关联商品）；
- `seckill_order`：两个唯一键 + `idx_order_user_id`（我的订单）、`idx_order_activity_status`（活动维度统计）、`idx_order_created_at`（榜单/分页排序）；
- `seckill_message_log`：`uk_stream_message`、`idx_message_status_retry(status,retry_count)`（扫描待重试/死信）、`idx_message_order_no`；
- `product`：`idx_product_status`、`idx_product_updated_at`。
**原则**：唯一键保正确性、查询索引保性能；写入路径（insert 订单）只维护必要的唯一索引。

---

## I. 性能与压测

### I1 ⭐ 压测参数与结果
**答**：JMeter 多用户参数化脚本（`scripts/jmeter/seckill_main.jmx`，每线程 `token,userId` 成对，避免"单令牌跑多用户"被 401）：**5000 个唯一用户请求，ramp 90s**。
结果：**0 错误**；吞吐约 55.5 req/s（ramp 限制）；延迟 avg 24ms / p50 17ms / p90 23ms / **p99 510ms**；后端守恒：成功订单 1000 = 库存，售罄拒绝 4000，`redisStock=0` → **无超卖、无丢单**。
**加分**：说明"单机压测客户端短 ramp 会有端口/TIME_WAIT 导致的连接失败，要区分客户端注入失败与系统错误；权威口径取后端计数"。

### I2 只有 1000 单成功算失败吗
**答**：不算，这是**正确行为**：库存 1000，成功单必须恰为 1000。判断"健康"靠**守恒关系**而不是成功率：`成功订单数 == 库存`、`成功 + 售罄拒绝 == 总请求`、`Redis 库存 == 0`、DB 无重复订单号。
**方法论点**：秒杀压测的核心指标不是 QPS，而是**库存精确性**（防超卖）与**守恒**（防丢单）。

### I3 压测暴露的问题
**答**：① **订单号并发碰撞**（见 H4）——表现为"库存 0 订单 999"，靠"订单数=库存"的守恒断言暴露，最终定位到时时钟源不一致；
② 客户端连接失败与业务拒绝混淆——改用后端计数为准；
③ 无自动消费导致订单恒 QUEUING——补 `StreamConsumerScheduler`。
**加分**：强调"压测的价值在于暴露**逻辑缺陷**（丢单/碰撞），不只是拿到 TPS 数字"。

### I4 ⭐ 瓶颈与 10 倍扩容
**答**：当前瓶颈按顺序：① **单实例 Redis**（所有库存/限流/Stream 都在这一个 key 空间，且 Lua 单线程）→ 拆 Cluster（按 activityId 分片，注意 Lua 多 key 要 hash tag，见 K2）；② **单库 MySQL**写入 → 分库分表（按 activity_id）+ 消息落库改批量；③ **消费速率** → 消费者水平扩展（消费组天然支持）+ 批量 XREADGROUP；④ 应用层无状态 → 多实例 + 网关层限流（Sentinel/Nginx）。
其他手段：库存分片、热点活动本地缓存、异步化非关键路径（指标/榜单）、限流前置到网关、DB 连接池与批量 insert。
**陷阱**：不要只答"加机器"，要说清**每个组件怎么横向扩展**、以及"哪些操作天然不可并行（Lua 单键）"。

### I5 连接池参数
**答**：Hikari `maximum-pool-size=20 / minimum-idle=5 / connection-timeout=10s / max-lifetime=30min`；Lettuce pool `max-active=16 / max-idle=8 / min-idle=4 / max-wait=2s`。
依据：秒杀主链**不查库**（Redis 承担热路径），DB 只是异步落库与配置读取，20 连接足够；Redis 是热路径核心，池子按"QPS × 单次耗时"估算并留余量，max-wait 防止无限阻塞（快速失败优于雪崩）。
**加分**：Lettuce 是 Netty 异步 + 共享连接，池主要解决阻塞式命令与多线程竞争。

---

## J. 安全与工程化

### J1 ⭐ 鉴权设计
**答**：统一 `ApiAccessInterceptor`：`/api/admin/**` 校验 `X-Admin-Token`；`execute` / `check` / `users/{id}/orders` 校验 `X-User-Token`，并**由令牌反解出绑定 userId**，与请求中的 userId 比对，不匹配直接 40100。
**为什么不信客户端 userId**：项目无用户表、userId 由前端透传——早期任意调用者可冒充任何用户下单/查别人订单。改为"令牌 → userId"的服务端映射后，冒充即失败（静态白名单 ∪ 动态注册表）。
**加分**：注册接口 `POST /api/auth/register` 动态发随机令牌（占用返 40903），且静态账号不可被抢占；令牌是内存态，重启失效需重领（演示取舍，生产应落库+过期）。

### J2 Redis 反序列化安全
**答**：`GenericJackson2JsonRedisSerializer` + DefaultTyping 会在 JSON 里写 `@class`，反序列化时按类型实例化——若 Redis 数据被污染，可能引导实例化 gadget 类（RCE 风险，审计 S2）。
加固：把 `LaissezFaireSubTypeValidator`（放任一切）换成 **`BasicPolymorphicTypeValidator` 白名单**，只放行 `com.ghb.ecommerceflashsalesystem.` 实体包与 `java.time/util/lang/math/net` 常用类型；再配合"Redis 只监听内网 + 设置密码（`REDIS_PASSWORD`）"做纵深防御。
**加分**：可以提"更彻底方案是自定义序列化器不写类型信息，或改用纯 JSON + 显式反序列化"。

### J3 上传校验
**答**：三重防线：① 扩展名白名单 + 5MB 上限；② **文件头魔数校验**（png/jpg/gif/bmp/webp）；③ 文件名由服务端 UUID 重写（防路径穿越），响应 Content-Type 由服务端按真实格式固定（不反射客户端值）。
**扩展名 vs 魔数谁说了算**：**魔数**。审计 E6 的根因就是"要求扩展名与文件头一致"——微信/网页另存等真实图片扩展名常与实际格式不符（.png 实为 JPEG），导致合法图片被误拒。改为"魔数优先"：内容验证为白名单图片即收，按真实格式改名存储；伪装脚本/PDF 依旧被拒，安全性不降。单测覆盖 8 个场景（含扩展名不符放行、无扩展名可收、伪装拒收）。
**陷阱**：不要说"扩展名和 MIME 都校验就够了"——MIME 是客户端可伪造的，必须看**内容字节**。

### J4 密钥管理
**答**：所有敏感项走环境变量 + `spring-dotenv` 读取模块目录下的 `.env`（gitignore，仓库只提交 `.env.example`）：`MYSQL_PASSWORD`、`REDIS_PASSWORD`、`ADMIN_TOKEN`、`USER_TOKENS`、`OSS_*`、`AI_LLM_API_KEY`；`application.yaml` 里全是 `${ENV:默认}` 占位，默认值仅供本地演示。
踩过的坑：① 环境变量被设成 `%OSS_ACCESS_KEY_ID%` 这类字面占位符 → 阿里云返回 `InvalidAccessKeyId`；② 命令行跑测试缺 `.env` 时出现 19 个 `Access denied`；③ 前端 `.env` 是**构建期**替换，改完必须重启 dev server（后端 `.env` 每次启动重读）。
**加分**：提到"曾硬编码的 AI Key 已清除并确认全历史未入库，建议轮换一次"——体现密钥卫生意识。

### J5 审计与回归保障
**答**：交付前做全量代码审计（后端核心链路/接口层/前端三路逐文件精读），产出带位置与建议的清单：**R1~R5 高危 + C1~C6 契约 + E1~E6 边界 + F1~F9 前端 + S1~S2 安全 + M1~M10 交付项 = 38 项全部闭环**，每批改动都跑全量 `mvn test`（92→98 用例）+ 前端 `vite build`，并把阻断性经验写进 plan.md「经验教训」。
**可复述的保障机制**：① 契约文档（interface.md）与代码对账；② 并发/守恒断言（请求量>库存、订单数=库存）；③ 回归纪律（跑全量前停 dev 后端、清键用 `!keys.isEmpty()`）。

---

## K. 场景设计 / 开放题

### K1 ⭐ 10 万 QPS 演进
**答**：分层扩容 + 削峰：
① **入口层**：CDN/静态化活动页，网关（Nginx/Sentinel）做 IP+用户限流、黑名单、验证码答题前置，挡掉 90% 无效流量；
② **应用层**：无状态多实例 + 负载均衡；热点活动配置进本地缓存（Caffeine）+ Redis 二级；
③ **Redis 层**：Cluster 分片（按 activityId），库存分片（N 个分片求和），Lua 用 hash tag 保证同活动同 slot；
④ **队列层**：换 Kafka/RocketMQ（高吞吐、堆积能力、顺序分区），消费端批量落库；
⑤ **DB 层**：分库分表 + 批量 insert + 最终一致性对账；订单号改号段/雪花；
⑥ **可观测**：全链路压测 + 熔断降级（Redis 挂了走"排队中占位+补偿"或直接降级拒绝）。
**表述技巧**：按"流量漏斗→计算→存储→可靠性"顺序讲，最后强调"秒杀的瓶颈永远在**单点写**（库存 key/DB），要靠分片与异步化解决"。

### K2 Cluster 下 Lua 多 key
**答**：Redis Cluster 把 key 按 slot 分片，Lua 脚本里的所有 KEYS 必须在**同一 slot**，否则报 `CROSSSLOT`。方案：用 **hash tag** 让相关 key 落在同一分片，例如把库存与令牌键写成 `seckill:{activityId}:stock` 和 `seckill:{activityId}:user:{userId}`（`{}` 内内容相同即同 slot）；或改脚本只操作单 key（把令牌合并进库存 Hash 的字段）。
本项目单实例不涉及，但键设计已经"活动维度聚类"，改造时有明确路径。

### K3 每人限购 N 件
**答**：三步改造：① DB 去掉/替换 `uk_activity_user` 唯一键（改为 `uk_activity_user_seq` 或增加数量列），因为一人多单与唯一键冲突；② Redis 令牌改为**计数器**（`INCR` + 上限判断，放进 Lua 与扣减同脚本，保证原子），键值语义从"存在即已购"变成"已购数量 < N"；③ 订单表加数量列，落库时校验累计数量。
**项目现状**：参数 `limitPerUser` 被 `@Min/@Max` 夹逼为 1（审计 E2），并在 DTO/服务层/前端三处一致提示"受 DB 唯一键约束"，避免"配了不生效"的误导——**这是"参数语义必须与存储约束一致"的好例子**。

### K4 对账与补偿任务
**答**：设计思路是"以 DB 终态为准做双向核对"：
① **正向**：扫 `seckill_message_log` 中 PENDING/FAILED 超时记录 → 查订单是否存在 → 不存在则回放消息或补偿库存/令牌；
② **反向**：扫 Redis 库存 + 订单数，若 `订单数 + 未完成消息数 != 库存初值` → 报警并人工介入；
③ **守恒校验**：定时任务比对"活动库存 - 已售 = Redis 剩余"，偏差超阈值告警；
④ **幂等保障**：补偿动作本身必须幂等（INCR/DEL、唯一键兜底），避免补偿把系统补偿坏。
本项目已有 `seckill_activity_snapshot` 定期快照表作为对账数据源（Day 2 埋点），以及死信回放服务，属于该体系的雏形。

### K5 多级缓存一致性
**答**：本地缓存（Caffeine，毫秒级、无网络开销）+ Redis（跨实例共享）；一致性用**发布订阅广播失效**：任何实例更新数据后 `PUBLISH cache:invalidate {key}`，各实例收到后删本地缓存；本地缓存 TTL 设短（秒级）作为兜底，容忍极端情况下的短暂不一致。
代价与取舍：本地缓存会带来"多实例视图不一致窗口"，所以**只缓存容忍短暂过期的数据**（活动配置、商品详情），**绝不放库存/令牌**这类强一致数据。

### K6 防刷体系
**答**：分层防御：① **前置**：验证码/答题、设备指纹、风控名单（黑 IP/设备）、活动页静态化 + 时间校验（未开始不可提交）；② **限流**：用户+IP+设备多维度滑窗（本项目限流键已参数化，可平滑扩展维度）；③ **业务**：一人一单令牌 + 幂等键（换 userId 也会被 DB 唯一键与设备指纹挡住）；④ **事后**：订单行为分析（同设备多账号、毫秒级规律请求）→ 人工审核与取消订单。
**加分**：说明"防刷是**成本对抗**，没有绝对方案，目标是提高攻击成本、降低误伤"。

---

## L. 复盘与软技能

### L1 ⭐ 排查最久的 bug
**答（示范：订单号碰撞）**：现象是压测报告里"库存归零但订单数差 1"，第一次以为是消费者丢消息，查 PEL 无异常、日志全 SUCCESS；后来加**守恒断言**（订单数必须等于库存）稳定复现，再用日志比对发现是 `uk_order_no` 重复被幂等吞掉；最终 code review 找到 `OrderNoGenerator` 的**双时钟源**问题（判定毫秒与拼接文本取的不是同一个时间），修成单一时钟源 + 单毫秒超限自旋等待。
**收获**：唯一键幂等会把"ID 碰撞"这种严重 bug 静默化成丢单，所以必须有独立守恒校验来兜监控——这也是我后来坚持"压测必须断言守恒"的原因。
（另一可选素材：Redis 容器"进程在、端口通但命令全超时"的假死，需 `docker restart` 恢复；或 `mvn test` 全量失败实为"dev 后端抢消费"的环境问题。）

### L2 ⭐ 不足与技术债
**答**（照实说，并给改进方案）：
① **单机 ID/订单号**（IdGenerator、OrderNoGenerator 单实例唯一）→ 多实例需 workerId/号段；
② **演示环境 Redis 无持久化**，`docker restart` 丢数据 → 生产开 RDB/AOF；
③ **榜单/指标/Stream 无 TTL**，长期运行内存单边增长 → 活动维度清理任务；
④ **无对账/补偿定时任务**（目前靠死信回放与人工核对）；
⑤ **AI 客服无 RAG、匿名无限流**；注册表无过期清理；
⑥ **可观测性弱**（无 Prometheus/链路追踪，指标只有活动维度计数）；
⑦ 集成测试依赖本机 MySQL+Redis（可改 Testcontainers）。
**表述**：先说"交付无阻塞"，再按"一致性与规模 → 安全 → 可观测"排序讲，每项带方案。

### L3 学习方法与验证
**答**：按"**问题驱动**"学：每类企业问题（缓存三兄弟/超卖/防重/削峰/限流/榜单）先写小实验验证原理（比如用并发测试证明 SETNX 的原子性、用真实滑窗清理验证 ZREMRANGEBYSCORE），再落到项目主线；每个模式都写了**可复现的验证手段**——并发测试（200 并发抢 100、50 线程同用户抢 1 单）、脚本级直测（Lua 返回码）、压测守恒断言，最后沉淀到 `docs/redis-summary.md`（问题→模式→代码位置索引）与 `docs/notework.md`（自己的话复述 + 踩坑）。
**加分**：强调"教学项目最大的价值是**每条结论都有对应的测试或压测证据**"。

### L4 最大收获
**答**：三点：① **正确性优先级**——先想清楚一致性边界（谁是权威、失败了怎么补偿），再谈性能；"落库才算抢到 + 所有失败路径都要补偿"是全项目的主线思维；② **原子性与幂等的分工**——Redis 提供原子，业务提供幂等，二者叠起来才可靠（唯有一层都不够）；③ **工程化习惯**——契约文档、（守恒）断言、全量回归、审计清单与经验教训沉淀，这些比会写某个 API 更重要。
**收尾一句**："这个项目让我把 Redis 从'会用 API'推进到'知道每种模式在什么条件下会失效、失效了怎么兜底'。"

---

### 附：回答节奏建议
- 每个问题先给**结论句**（10 秒），再讲**为什么/怎么做**（30~60 秒），最后补**项目证据**（代码位置/测试数字）；
- 被追问细节时优先引用**真实数字与文件名**（如"200 并发抢 100，成功恰 100"、"`seckill_execute.lua` 返回 -2 代表重复"），可信度远高于空泛描述；
- 不会的问题直说边界（"这块我在项目里没做，我的理解是…"），比硬答安全。
