# 面试速查卡（一页版）· 轻量级电商秒杀系统

> 用法：面试前 10 分钟扫一遍；括号内/表格里均为**可复述的证据**，追问时优先报数字与文件名。
> 配套：`docs/interview-questions.md`（67 题自测）· `docs/interview-answers.md`（答案与深度讲解）。

---

## 一、数字速记（必须张口就来）

| 项 | 数值（证据） |
|---|---|
| 全链路压测 | **5000 请求 / 90s ramp / 0 错误**；p50 17ms、avg 24ms、p90 23ms、**p99 510ms**（`docs/load-test-report.md`） |
| 压测守恒 | 成功订单 **1000 = 配置库存**；售罄拒绝 **4000**；`seckill:stock` **精确归零**；message_log 1000/1000 无重复 → 0 超卖 / 0 丢单 / 0 重单 |
| 并发防超卖 | **200 并发抢 100**：成功恰 100、拒绝恰 100、库存 0（`SeckillExecuteConcurrencyTest`） |
| 并发防重 | 同用户 **50 线程**并发仅 **1 次成功**，49 次 40901（`SeckillUserDedupTest`） |
| 预热并发 | **8 线程**并发预热仅 1 成功、`update` 只调 1 次、锁最终释放（`PreheatConcurrencyTest`） |
| 缓存穿透 | 不存在 ID 连查 **100 次**，仅打库 **1 次**（`testCachePenetration`） |
| 自动化用例 | **92 全绿 BUILD SUCCESS**（E6 收尾批 +6，回归后 **98**，待开发机确认） |
| 代码审计 | **38 项闭环**（R5+C6+E6+F9+S2+M10），含 5 项高危 R1~R5 |
| TTL | 商品缓存 **300+random(60)s**；空值标记 **60s**；幂等令牌 **1800s**；限流键 `window+1` |
| 限流 | **60s / 5 次**，键 `rate:limit:{activityId}:{userId}`，`flash.rate-limit.*` 可配 |
| 分布式锁 | 商品回源 `wait 2s / lease 30s`；预热与库存重置 `wait 5s / lease 30s`（显式 lease → 看门狗不生效） |
| 消费调度 | 新消息 **150ms**（批 64）／PEL 重试 **5s**（批 32）；`MAX_RETRY=3`（**第 3 次失败**转死信）；error_message 截断 **1023** |
| 连接池 | Hikari `20/5`；Lettuce `max-active 16 / idle 8 / min-idle 4 / wait 2s` |
| 业务码 | 0 / 40001 / 40004 / 40901 重复 / 40902 售罄 / 42900 限流 / 40100 令牌无效 / 40903 注册占用 / 50300·50301 AI |

---

## 二、键位速查（键 → 类型 → TTL → 用途）

| 键 | 类型 | TTL | 用途 |
|---|---|---|---|
| `product:detail:{id}` | String | 300+rand(60)s | 商品缓存旁路（三态：真实值 / `""` 空值 / 未命中） |
| `seckill:activity:{id}` | Hash | TTL+60s | 活动信息与时间窗（预热写入） |
| `seckill:stock:{id}` | String | 随活动 | 实时库存（Lua 原子扣减） |
| `seckill:user:{aid}:{uid}` | String | 1800s | 幂等令牌（排队资格；售罄回滚） |
| `rate:limit:{aid}:{uid}` | ZSet | window+1 | 滑动窗口限流（活动×用户双维） |
| `seckill:order:stream` | Stream | 无（待治理） | 下单削峰队列（消费组 `seckill:order:group`） |
| `seckill:order:dead:stream` | Stream | 无 | 死信流（明文 Map + originalMessageId + 错误） |
| `seckill:rank:{aid}` | ZSet | 无 | 成功榜（member=userId，score=requestTime，ZADD NX） |
| `seckill:metric:{aid}` | Hash | 无 | 三类拒绝计数（rateLimit/duplicate/soldOut） |
| `seckill:lock:preheat:{id}` / `reset:{id}` | String(锁) | 30s lease | 预热 / 库存重置写入口 |

---

## 三、关键决策「为什么」一句话版

| 决策 | 一句话理由 |
|---|---|
| 限流放**最前** | 最便宜的原子操作先挡刷子；顺序反了会让被限流请求白耗库存/令牌（错误码也应是 42900 而非 40901） |
| execute **不加锁** | 防重+扣减已是单键 Lua 原子；加锁把原子操作降级为锁排队 = 负优化（锁只护"多步读改写"） |
| 用 **Lua** 而非先查后扣 | `GET` 与 `DECR` 之间有 check-then-act 竞态；脚本整体原子执行，且把「建令牌+扣减+回滚」合成一次执行 |
| 更新时**删缓存**而非更新缓存 | 并发双写会覆盖出脏缓存；删除幂等可重试；读多写少代价可接受 |
| 榜单用 **ZADD NX** 而非 ZINCRBY | NX 天然幂等（重放/补录不重复计分）；ZINCRBY 必须严格只调一次 |
| Stream 投递用**明文 Map** | `objectBacked` 会让字段被序列化成 Base64（`200`→`"MjAw"`），消费端直接 NumberFormatException |
| **落地才算抢到** | execute 只是资格；订单落库才入榜/算成交；失败必须回滚或补偿（DB 终态为准） |
| **不 ACK = 重试** | 未 ACK 消息留在 PEL，`STREAMS stream 0` 可重读；达阈值必须"转死信 + ACK"给 PEL 出口 |
| 上传**魔数优先** | 扩展名是客户端可伪造的；微信/网页另存常"扩展名≠真实格式"，要求一致会误拒合法图片（E6） |
| ID **< 2^53** | 超 JS 安全整数会让活动/订单 ID 在前端失真，导致详情/榜单 404（R1） |
| 反序列化**白名单** | DefaultTyping 的 `@class` 可被污染引导实例化 gadget 类（S2 加固） |
| 鉴权**令牌绑定 uid** | 项目无用户表、userId 由前端透传；不校令牌即可冒充任意用户下单/查单（R3） |

---

## 四、高频追问一句话答（12 条）

1. **消息真不会丢？** → 至少一次：ACK 前都在 PEL，崩溃可重读；Redis 无持久化仍可能丢实例级数据，生产开 AOF/RDB 或换 Kafka，DB 唯一键与对账兜最终一致。
2. **重复消费怎么办？** → 四层幂等：日志终态短路（SUCCESS/DEAD 直接 ACK）、`selectByOrderNo` 查重、唯一键兜底、ZADD NX。
3. **死信回放会不会超卖？** → 不会：回放先执行同一 Lua 重新原子占位（库存不足 `-1`、已有令牌 `-2` 直接拒绝），订单已落库也拒绝回放；重投失败会归还占位。
4. **PEL 会膨胀吗？** → 有出口：`retry_count` 达 3 就"转死信 + ACK"；解析失败也纳入计数（R5 修的就是"毒消息只 log 不 ACK"）。
5. **为什么不用 XAUTOCLAIM？** → 单消费者场景 `STREAMS stream 0` 够用；多实例应升级 XAUTOCLAIM + 处理超时（主动作为改进项提）。
6. **补偿失败怎么办？** → 记含 stockKey/tokenKey 的 error 日志 + 对账（守恒校验）发现偏差人工介入；这是我承认的技术债之一。
7. **看门狗还生效吗？** → 显式传了 leaseTime（30s）→ 看门狗不续期，到点自动释放；业务耗时受此约束。
8. **为什么不用 Kafka？** → 教学一站一模式、零额外部署；Stream 已具备消费组/PEL/ACK。换 MQ 时业务幂等与补偿逻辑不用改。
9. **限流换 userId 能绕过吗？** → 能，单 userId 维度不够；生产要叠 IP/设备指纹 + 风控（限流键已参数化便于扩展）。
10. **缓存与 DB 强一致吗？** → 不做强一致：Cache Aside + 更新删缓存，接受"一次回源"窗口；资金类字段（成交价）随消息快照下发，不依赖缓存。
11. **为什么不用数据库乐观锁扣库存？** → 高冲突场景重试风暴；乐观锁只用于低频的活动配置更新（`@Version`）。
12. **5000 压测只成功 1000 算通过吗？** → 算：库存 1000，靠守恒判定（成功=库存、成功+拒绝=总请求、Redis 库存=0），秒杀压测看**库存精确性**而非成功率。

---

## 五、陷阱 / 易错清单（复盘时自查）

- **Redis 用 db1**：排查要 `redis-cli -n 1`，连默认 db0 会误判"键不存在"。
- **清键**：`keys(prefix + "*")` 要带通配符；删除判空必须 `!keys.isEmpty()`（写反永远清不掉）。
- **跑全量测试**：先停 IDE 里的 dev 后端（auto-poll 会抢走测试写入 Stream 的消息，表现为"随机少一条"的假 flaky）。
- **测试隔离**：凡调 execute 的用例要清 `rate:limit:*` + `seckill:user:*` + `seckill:order:stream`；坏消息用例的 `BAD_ACTIVITY_ID` 行要删（否则外键不再失败）。
- **Lua 参数**：脚本 ARGV 传数值，别传 String（序列化会加引号，`tonumber` 得 nil，静默降级）。
- **`MAX_RETRY=3` 语义**：是"第 3 次处理失败即转死信"（首次 + 2 次重试），不是"失败 4 次"。
- **消费实例名**：别用 `System.getenv("HOSTNAME")`（Windows 拼出 `consumer-null-*`），用 UUID。
- **建组幂等**：BUSYGROUP 藏在异常 cause 链里（外层只是笼统 "Error in execution"）。
- **Redis 容器无持久化**：`docker restart` 丢全部 key → 建组必须幂等；正式部署开持久化。
- **只 ACK 不落库**是本项目真实踩过的最高危 bug（重构时丢了 `insert`），review 必查副作用语句。
- **审计口径**：38 项 = R5 + C6 + E6 + F9 + S2 + M10；O1~O10 为**开放建议**（可主动作为"技术债"回答）。
- **用例口径**：92 为已实证；E6 后 98 待回归 —— 面试说 92 稳妥，说 98 前先跑一遍。

---

## 六、两分钟口述骨架（主链 → 可靠性 → 数据）

1. **一句话定位**：教学向但工程完整的秒杀系统，把六类 Redis 企业模式各落地一个并验证。
2. **主链**（顺序 + 理由）：限流(ZSet 双维) → 活动缓存+时间窗 → Lua 原子（令牌+扣减+回滚）→ XADD → 返回排队中 → 消费组异步落库 → 入榜。
3. **可靠性**：PEL 重试 + 死信 + 三类补偿 + 人工回放 + 消息日志三态（"不丢/不重/可兜底"）。
4. **数据**：四道幂等闸（令牌/查重/唯一键/XACK）+ "落库才算抢到"，压测守恒证明。
5. **收尾**：5000 请求 0 错误、库存精确归零；92 用例全绿；审计 38 项闭环；并主动说 1~2 项技术债与改进方案。
