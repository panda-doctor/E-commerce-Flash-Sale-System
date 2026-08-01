### 一、项目背景与目标

模拟一个高并发商品秒杀场景，核心目标是：

- 用 Redis 解决**高性能读写**、**库存超卖**、**流量削峰**、**接口防刷**等问题。
- 项目足够简单，方便快速搭建，但能深入理解 Redis 在企业级应用中的核心模式。

------

### 二、技术架构与栈

| 层级         | 技术选型                  | 说明                         |
| :----------- | :------------------------ | :--------------------------- |
| 后端框架     | Spring Boot 2.7+          | 快速构建 RESTful API         |
| 数据库       | MySQL 8.0 + MyBatis-Plus  | 持久化订单、商品信息         |
| 缓存与中间件 | **Redis 7.0** + Redisson  | 核心：缓存、锁、队列、计数器 |
| 消息队列     | Redis Streams (轻量级)    | 异步下单，削峰填谷           |
| 前端         | Thymeleaf + Vue.js (可选) | 简易页面展示秒杀按钮与结果   |
| 工具         | JMeter 或 wrk             | 并发压测验证方案             |

------

### 三、功能模块设计

1. **商品管理**：管理员预加载秒杀商品到 Redis。
2. **秒杀活动**：设定开始/结束时间，预热库存到 Redis。
3. **用户秒杀**：点击抢购，后端经过多层 Redis 防护。
4. **订单生成**：异步消费 Redis 消息，生成订单落库。
5. **实时排行榜**：用 Redis ZSet 展示秒杀成功用户排名。

------

### 四、Redis 解决方案映射（核心学习点）

以下每个问题都对应一个企业级 Redis 用法：

| 企业问题                   | Redis 解决方案              | 项目中的具体实现                                             |
| :------------------------- | :-------------------------- | :----------------------------------------------------------- |
| 高并发读——商品信息频繁查询 | **缓存旁路模式**            | 商品详情先查 Redis，未命中则查 MySQL 并回写，设置随机过期时间防雪崩。 |
| 秒杀库存超卖               | **原子扣减 + Lua 脚本**     | 库存预热到 Redis String，扣减时执行 `DECR` 或 Lua 判断后扣减，保证原子性。 |
| 一人多抢/重复下单          | **分布式锁 + 用户唯一标记** | 用 Redisson 的 `RLock` 对 `userId+goodsId` 加锁，或用 `SETNX` 幂等令牌。 |
| 瞬时流量冲击后端           | **Redis 队列削峰**          | 秒杀请求校验通过后，直接写入 Redis Stream，由后端消费者匀速处理。 |
| 恶意刷单/限流              | **滑动窗口计数器**          | 使用 Redis ZSet 实现接口级滑动窗口限流，每分钟单用户只能请求 N 次。 |
| 实时热榜                   | **Sorted Set**              | 秒杀成功后 `ZINCRBY` 用户得分，排行榜用 `ZREVRANGE` 查询前10。 |
| 系统解耦/最终一致性        | **消息队列**                | Redis Stream 消费者组保证下单消息被可靠消费，失败可重试。    |

------

### 五、核心流程设计（简版秒杀链路）

```
[用户请求] 
    → 1. 滑动窗口限流 (Redis ZSet) 
    → 2. 验证活动时间 (Redis 缓存) 
    → 3. 用户去重幂等 (Redis Set + 分布式锁)
    → 4. 库存预扣减 (Lua脚本原子操作) 
    → 5. 写入 Redis Stream 消息 
    → 6. 异步消费者读取消息，生成订单落 MySQL
```

每一步都是 Redis 的实战应用，你可以单步调试学习。

------

### 六、项目实施步骤建议

1. **环境准备**：Docker 快速搭建 MySQL + Redis，搭建 Spring Boot 骨架。
2. **基础缓存**：实现商品信息查询的缓存旁路，理解穿透/击穿/雪崩。
3. **库存原子扣减**：纯 Redis 库存扣减逻辑（Lua 脚本）。
4. **防超卖测试**：用 JMeter 模拟 1000 并发，验证库存不会变负。
5. **引入队列**：把下单改为 Stream 发送，消费者落库。
6. **加分布式锁**：防同一用户重复秒杀。
7. **滑动窗口限流**：限制单用户每秒最多 1 次秒杀请求。
8. **排行榜实现**：秒杀成功写入 ZSet，页面展示。

------

### 七、示例代码片段（关键模式）

**Lua 脚本扣库存（原子检查与扣减）**

```lua
local key = KEYS[1]     -- 库存键
local uid = ARGV[1]
local stock = tonumber(redis.call('get', key))
if stock and stock > 0 then
    redis.call('decr', key)
    return 1  -- 扣减成功
else
    return 0  -- 库存不足
end
```

**Redisson 分布式锁防重**

```java
RLock lock = redissonClient.getLock("seckill:lock:" + userId + ":" + goodsId);
if (lock.tryLock(0, 5, TimeUnit.SECONDS)) {
    try { /* 检查是否已购买，执行扣库存 */ }
    finally { lock.unlock(); }
}
```

**滑动窗口限流（Lua 实现）**

```lua
local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)
if count < limit then
    redis.call('ZADD', key, now, now .. '-' .. math.random())
    redis.call('EXPIRE', key, window)
    return 1
else
    return 0
end
```

------

这个项目架构轻量但五脏俱全，学完它，你就能理解 Redis 在秒杀、排行榜、限流、分布式锁等绝大多数高并发场景中的工业级实践。
