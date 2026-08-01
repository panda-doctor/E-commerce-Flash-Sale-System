# 轻量级电商秒杀系统数据库设计

## 一、设计目标

数据库只负责保存长期事实数据：商品、秒杀活动、订单、消息消费记录和指标快照。高频变化的数据，例如库存预扣减、用户限流窗口、用户秒杀令牌、订单消息队列和排行榜，优先放在缓存系统中处理。

这样设计的目标是：

- 避免高并发秒杀请求直接打到数据库。
- 用数据库唯一索引兜底防止重复下单。
- 用消息日志和指标快照支持问题排查、压测复盘和后续学习。

## 二、表清单

| 表名 | 说明 | 核心用途 |
| --- | --- | --- |
| `product` | 商品表 | 保存商品基础信息 |
| `seckill_activity` | 秒杀活动表 | 保存活动时间、价格、秒杀库存和状态 |
| `seckill_order` | 秒杀订单表 | 保存最终生成的秒杀订单 |
| `seckill_message_log` | 秒杀消息消费日志表 | 辅助追踪异步下单消息是否成功消费 |
| `seckill_activity_snapshot` | 秒杀活动指标快照表 | 保存压测和演示阶段的运行指标 |

## 三、表结构说明

### 3.1 商品表

表名：`product`

保存商品基础信息。秒杀价格不直接放在商品表中，而是放在活动表中，因为同一个商品可能参与不同活动，不同活动可以有不同价格和库存。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 商品编号 |
| `name` | 商品名称 |
| `description` | 商品描述 |
| `image_url` | 商品图片地址 |
| `original_price` | 原价，单位分 |
| `total_stock` | 商品总库存 |
| `available_stock` | 普通可用库存 |
| `status` | 商品状态，`0` 下架，`1` 上架 |

索引设计：

| 索引 | 说明 |
| --- | --- |
| `PRIMARY KEY (id)` | 商品主键 |
| `idx_product_status` | 按商品状态筛选 |
| `idx_product_updated_at` | 支持后续缓存刷新或后台排序 |

### 3.2 秒杀活动表

表名：`seckill_activity`

保存秒杀活动配置。后续活动预热时，会把活动时间窗口和秒杀库存写入缓存。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 活动编号 |
| `product_id` | 商品编号 |
| `activity_name` | 活动名称 |
| `start_time` | 活动开始时间 |
| `end_time` | 活动结束时间 |
| `seckill_price` | 秒杀价，单位分 |
| `seckill_stock` | 秒杀库存 |
| `limit_per_user` | 单个用户限购数量 |
| `status` | `0` 未开始，`1` 进行中，`2` 已结束，`3` 已售罄，`4` 已取消 |
| `preheat_status` | `0` 未预热，`1` 已预热，`2` 预热失败 |
| `version` | 版本号，用于后台更新控制 |

索引设计：

| 索引 | 说明 |
| --- | --- |
| `idx_activity_product_id` | 查询某商品关联活动 |
| `idx_activity_time_status` | 按时间窗口和状态查询活动 |
| `idx_activity_status` | 后台按活动状态筛选 |

### 3.3 秒杀订单表

表名：`seckill_order`

保存异步消费者最终创建出来的订单。秒杀请求进入消息队列后，前端可以通过订单编号轮询订单状态。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 订单主键 |
| `order_no` | 订单编号 |
| `activity_id` | 活动编号 |
| `product_id` | 商品编号 |
| `user_id` | 用户编号 |
| `seckill_price` | 成交价，单位分 |
| `status` | `0` 排队中，`1` 已创建，`2` 创建失败，`3` 已取消 |
| `stream_message_id` | 缓存消息编号 |
| `failure_reason` | 失败原因 |

索引设计：

| 索引 | 说明 |
| --- | --- |
| `uk_order_no` | 保证订单编号唯一 |
| `uk_activity_user` | 数据库层兜底防止同一用户同一活动重复下单 |
| `idx_order_user_id` | 查询用户订单 |
| `idx_order_activity_status` | 查询活动下不同状态的订单 |
| `idx_order_created_at` | 后续按时间统计订单 |

### 3.4 秒杀消息消费日志表

表名：`seckill_message_log`

用于记录异步下单消息的消费状态。虽然消息队列本身在缓存中，但消费失败、重试次数、死信流转等信息可以落到数据库，方便排查。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `stream_key` | 消息流键 |
| `stream_message_id` | 消息编号 |
| `consumer_group` | 消费者组 |
| `consumer_name` | 消费者名称 |
| `order_no` | 订单编号 |
| `activity_id` | 活动编号 |
| `product_id` | 商品编号 |
| `user_id` | 用户编号 |
| `retry_count` | 重试次数 |
| `status` | `0` 待消费，`1` 消费成功，`2` 消费失败，`3` 已进入死信 |
| `error_message` | 错误信息 |

索引设计：

| 索引 | 说明 |
| --- | --- |
| `uk_stream_message` | 防止同一消息重复记录 |
| `idx_message_activity_user` | 按活动和用户排查消息 |
| `idx_message_status_retry` | 查询待重试或失败消息 |
| `idx_message_order_no` | 通过订单编号反查消息 |

### 3.5 秒杀活动指标快照表

表名：`seckill_activity_snapshot`

用于记录压测、演示和复盘阶段的活动运行指标。它不是秒杀主链路必需表，不建议在每次请求中同步写入。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `activity_id` | 活动编号 |
| `redis_stock` | 缓存库存 |
| `order_count` | 订单数量 |
| `queued_message_count` | 队列积压消息数 |
| `success_count` | 成功数量 |
| `duplicate_reject_count` | 重复请求拒绝数量 |
| `rate_limit_reject_count` | 限流拒绝数量 |
| `sold_out_reject_count` | 售罄拒绝数量 |
| `snapshot_time` | 快照时间 |

索引设计：

| 索引 | 说明 |
| --- | --- |
| `idx_snapshot_activity_time` | 查询某活动在一段时间内的指标变化 |

## 四、和缓存键的关系

| 数据库表 | 对应缓存键 | 说明 |
| --- | --- | --- |
| `product` | `product:detail:{productId}` | 商品详情缓存 |
| `seckill_activity` | `seckill:activity:{activityId}` | 活动信息和时间窗口 |
| `seckill_activity` | `seckill:stock:{activityId}` | 秒杀库存预热 |
| `seckill_order` | `seckill:user:{activityId}:{userId}` | 用户秒杀幂等标记 |
| `seckill_order` | `seckill:rank:{activityId}` | 订单创建成功后写入排行榜 |
| `seckill_message_log` | `seckill:order:stream` | 记录消息消费结果 |

## 五、关键约束

### 5.1 防重复下单

业务层使用缓存令牌或分布式锁防止重复请求，数据库层使用以下唯一索引兜底：

```sql
UNIQUE KEY uk_activity_user (activity_id, user_id)
```

这能保证同一个用户在同一个活动中最多只有一笔订单。

### 5.2 防止数据库承压

秒杀请求不直接扣数据库库存。推荐链路是：

```text
活动预热到缓存
  -> 秒杀请求进入接口
  -> 缓存限流
  -> 缓存幂等校验
  -> 脚本原子扣减缓存库存
  -> 写入消息流
  -> 消费者异步创建订单
```

数据库只在消费者阶段写订单，避免瞬时流量直接冲击数据库。

### 5.3 库存一致性

活动表中的 `seckill_stock` 表示活动配置库存，缓存中的 `seckill:stock:{activityId}` 表示秒杀实时可扣库存。压测后可以通过以下关系复核：

```text
活动配置库存 = 已成功订单数 + 缓存剩余库存 + 售罄之后未进入队列的拒绝量
```

其中售罄拒绝量只用于指标观察，不参与订单一致性计算。

## 六、开发阶段建议

第一阶段先建：

- `product`
- `seckill_activity`
- `seckill_order`

第二阶段加入：

- `seckill_message_log`

第三阶段压测和演示时再使用：

- `seckill_activity_snapshot`
