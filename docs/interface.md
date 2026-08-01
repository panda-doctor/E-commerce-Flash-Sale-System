# 轻量级电商秒杀系统接口文档

## 一、文档目标

本文档用于约定项目后续开发中的接口边界。接口设计围绕项目核心方向展开：商品缓存、秒杀活动、库存预热、限流防刷、原子扣减、异步下单、订单查询和实时排行榜。

当前接口按完整目标设计，实际开发可按阶段逐步实现。

## 二、基础约定

### 2.1 基础地址

本地开发默认地址：

```text
http://localhost:8080
```

统一接口前缀：

```text
/api
```

### 2.2 请求格式

除特殊说明外：

- 请求体使用 `application/json`
- 响应体使用 `application/json`
- 时间使用毫秒级时间戳或 `yyyy-MM-dd HH:mm:ss`
- 金额使用分为单位，避免浮点误差

### 2.3 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "202607271200001001",
  "timestamp": 1785124800000
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | 数字 | 业务状态码，`0` 表示成功 |
| `message` | 字符串 | 响应说明 |
| `data` | 对象 | 响应数据 |
| `requestId` | 字符串 | 请求追踪编号 |
| `timestamp` | 数字 | 服务端响应时间戳 |

### 2.4 常用业务状态码

| 状态码 | 说明 |
| --- | --- |
| `0` | 成功 |
| `40001` | 参数错误 |
| `40004` | 资源不存在 |
| `40901` | 重复秒杀 |
| `40902` | 库存不足 |
| `42900` | 请求过于频繁 |
| `50000` | 系统异常 |

### 2.5 常用状态枚举

秒杀活动状态：

| 值 | 说明 |
| --- | --- |
| `NOT_STARTED` | 未开始 |
| `RUNNING` | 进行中 |
| `ENDED` | 已结束 |
| `SOLD_OUT` | 已售罄 |

秒杀请求结果：

| 值 | 说明 |
| --- | --- |
| `QUEUED` | 已进入队列 |
| `SOLD_OUT` | 已抢光 |
| `DUPLICATED` | 重复秒杀 |
| `RATE_LIMITED` | 请求过于频繁 |
| `ACTIVITY_CLOSED` | 活动未开放 |

订单状态：

| 值 | 说明 |
| --- | --- |
| `PENDING` | 排队中 |
| `CREATED` | 已创建 |
| `FAILED` | 创建失败 |

## 三、接口清单

| 模块 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 健康检查 | `GET` | `/api/health` | 检查服务状态 |
| 商品 | `GET` | `/api/products/{productId}` | 查询商品详情，使用缓存旁路 |
| 管理端商品 | `POST` | `/api/admin/products` | 创建或更新商品 |
| 管理端活动 | `POST` | `/api/admin/seckill/activities` | 创建秒杀活动 |
| 管理端预热 | `POST` | `/api/admin/seckill/activities/{activityId}/preheat` | 预热活动和库存到缓存 |
| 秒杀活动 | `GET` | `/api/seckill/activities/{activityId}` | 查询活动详情 |
| 秒杀校验 | `GET` | `/api/seckill/activities/{activityId}/check` | 查询用户是否可参与 |
| 秒杀执行 | `POST` | `/api/seckill/execute` | 执行秒杀请求 |
| 订单查询 | `GET` | `/api/seckill/orders/{orderNo}` | 查询订单状态 |
| 用户订单 | `GET` | `/api/seckill/users/{userId}/orders` | 查询用户秒杀订单 |
| 排行榜 | `GET` | `/api/rank/top10` | 查询秒杀成功排行榜 |
| 管理端指标 | `GET` | `/api/admin/seckill/activities/{activityId}/metrics` | 查询活动运行指标 |

## 四、接口详情

### 4.1 健康检查

```text
GET /api/health
```

用途：检查应用、数据库和缓存是否可用。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "application": "UP",
    "mysql": "UP",
    "redis": "UP"
  },
  "requestId": "202607271200001001",
  "timestamp": 1785124800000
}
```

### 4.2 查询商品详情

```text
GET /api/products/{productId}
```

用途：查询商品信息。后续实现时应采用缓存旁路模式，先查缓存，未命中再查数据库并回写缓存。

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productId` | 数字 | 是 | 商品编号 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "productId": 1,
    "name": "秒杀机械键盘",
    "description": "高并发秒杀测试商品",
    "originalPrice": 29900,
    "seckillPrice": 9900,
    "status": "ON_SHELF",
    "cacheHit": true
  },
  "requestId": "202607271200001002",
  "timestamp": 1785124800000
}
```

### 4.3 创建或更新商品

```text
POST /api/admin/products
```

用途：管理端创建或更新商品信息。

请求体：

```json
{
  "productId": 1,
  "name": "秒杀机械键盘",
  "description": "高并发秒杀测试商品",
  "originalPrice": 29900,
  "seckillPrice": 9900,
  "totalStock": 100,
  "status": "ON_SHELF"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "productId": 1
  },
  "requestId": "202607271200001003",
  "timestamp": 1785124800000
}
```

### 4.4 创建秒杀活动

```text
POST /api/admin/seckill/activities
```

用途：创建秒杀活动，配置商品、库存、开始时间和结束时间。

请求体：

```json
{
  "activityId": 1,
  "productId": 1,
  "activityName": "键盘限时秒杀",
  "startTime": "2026-07-27 20:00:00",
  "endTime": "2026-07-27 21:00:00",
  "seckillStock": 100,
  "limitPerUser": 1
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "status": "NOT_STARTED"
  },
  "requestId": "202607271200001004",
  "timestamp": 1785124800000
}
```

### 4.5 预热秒杀活动

```text
POST /api/admin/seckill/activities/{activityId}/preheat
```

用途：将活动信息、活动时间窗口和秒杀库存预热到缓存。

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `activityId` | 数字 | 是 | 活动编号 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "stockKey": "seckill:stock:1",
    "activityKey": "seckill:activity:1",
    "stock": 100
  },
  "requestId": "202607271200001005",
  "timestamp": 1785124800000
}
```

### 4.6 查询秒杀活动详情

```text
GET /api/seckill/activities/{activityId}
```

用途：查询活动详情、时间窗口和当前缓存库存。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "productId": 1,
    "activityName": "键盘限时秒杀",
    "startTime": "2026-07-27 20:00:00",
    "endTime": "2026-07-27 21:00:00",
    "status": "RUNNING",
    "stock": 88,
    "limitPerUser": 1
  },
  "requestId": "202607271200001006",
  "timestamp": 1785124800000
}
```

### 4.7 查询用户秒杀资格

```text
GET /api/seckill/activities/{activityId}/check?userId=1001
```

用途：检查活动是否开放、用户是否重复秒杀、是否触发限流。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | 数字 | 是 | 用户编号 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "userId": 1001,
    "canJoin": true,
    "activityStatus": "RUNNING",
    "reason": "ALLOW"
  },
  "requestId": "202607271200001007",
  "timestamp": 1785124800000
}
```

### 4.8 执行秒杀

```text
POST /api/seckill/execute
```

用途：秒杀核心接口。后续实现顺序应为滑动窗口限流、活动校验、用户幂等、库存原子预扣减、写入队列。

请求体：

```json
{
  "activityId": 1,
  "productId": 1,
  "userId": 1001
}
```

成功响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "result": "QUEUED",
    "orderNo": "SK202607272000001001",
    "message": "秒杀请求已进入队列"
  },
  "requestId": "202607271200001008",
  "timestamp": 1785124800000
}
```

库存不足响应示例：

```json
{
  "code": 40902,
  "message": "库存不足",
  "data": {
    "result": "SOLD_OUT"
  },
  "requestId": "202607271200001009",
  "timestamp": 1785124800000
}
```

重复秒杀响应示例：

```json
{
  "code": 40901,
  "message": "请勿重复秒杀",
  "data": {
    "result": "DUPLICATED"
  },
  "requestId": "202607271200001010",
  "timestamp": 1785124800000
}
```

限流响应示例：

```json
{
  "code": 42900,
  "message": "请求过于频繁",
  "data": {
    "result": "RATE_LIMITED"
  },
  "requestId": "202607271200001011",
  "timestamp": 1785124800000
}
```

### 4.9 查询订单状态

```text
GET /api/seckill/orders/{orderNo}
```

用途：秒杀请求入队后，前端可轮询此接口查询订单最终状态。

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderNo` | 字符串 | 是 | 订单编号 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderNo": "SK202607272000001001",
    "activityId": 1,
    "productId": 1,
    "userId": 1001,
    "status": "CREATED",
    "createdAt": "2026-07-27 20:00:01"
  },
  "requestId": "202607271200001012",
  "timestamp": 1785124800000
}
```

### 4.10 查询用户秒杀订单

```text
GET /api/seckill/users/{userId}/orders?activityId=1
```

用途：查询指定用户在指定活动下的订单。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "orderNo": "SK202607272000001001",
      "activityId": 1,
      "productId": 1,
      "status": "CREATED",
      "createdAt": "2026-07-27 20:00:01"
    }
  ],
  "requestId": "202607271200001013",
  "timestamp": 1785124800000
}
```

### 4.11 查询秒杀排行榜

```text
GET /api/rank/top10?activityId=1
```

用途：查询秒杀成功用户前十名。后续实现使用有序集合。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `activityId` | 数字 | 是 | 活动编号 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "rank": 1,
      "userId": 1001,
      "score": 1,
      "orderNo": "SK202607272000001001"
    }
  ],
  "requestId": "202607271200001014",
  "timestamp": 1785124800000
}
```

### 4.12 查询秒杀活动运行指标

```text
GET /api/admin/seckill/activities/{activityId}/metrics
```

用途：查询压测和演示阶段需要观察的核心指标。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "redisStock": 88,
    "orderCount": 12,
    "queuedMessageCount": 0,
    "successCount": 12,
    "duplicateRejectCount": 3,
    "rateLimitRejectCount": 5,
    "soldOutRejectCount": 0
  },
  "requestId": "202607271200001015",
  "timestamp": 1785124800000
}
```

## 五、核心链路约定

秒杀接口 `/api/seckill/execute` 的后续实现应遵循以下顺序：

```text
用户请求
  -> 滑动窗口限流
  -> 校验活动时间
  -> 校验用户是否重复秒杀
  -> Lua 原子扣减缓存库存
  -> 写入 Redis Stream
  -> 返回排队中
  -> 消费者异步创建订单
  -> 写入排行榜
```

建议缓存键：

| 键 | 类型 | 说明 |
| --- | --- | --- |
| `product:detail:{productId}` | 字符串 | 商品详情缓存 |
| `seckill:activity:{activityId}` | 哈希 | 秒杀活动信息 |
| `seckill:stock:{activityId}` | 字符串 | 秒杀库存 |
| `seckill:user:{activityId}:{userId}` | 字符串 | 用户秒杀幂等标记 |
| `rate:limit:{userId}` | 有序集合 | 用户限流窗口 |
| `seckill:order:stream` | 流 | 秒杀订单消息 |
| `seckill:order:dead:stream` | 流 | 消费失败死信消息 |
| `seckill:rank:{activityId}` | 有序集合 | 秒杀成功排行榜 |

## 六、阶段实现建议

第一阶段先实现：

- `/api/health`
- `/api/products/{productId}`
- `/api/admin/products`

第二阶段实现：

- `/api/admin/seckill/activities`
- `/api/admin/seckill/activities/{activityId}/preheat`
- `/api/seckill/activities/{activityId}`
- `/api/seckill/execute`

第三阶段增强：

- 用户幂等
- 分布式锁
- 滑动窗口限流
- `Redis Stream` 异步下单

第四阶段补齐：

- `/api/seckill/orders/{orderNo}`
- `/api/seckill/users/{userId}/orders`
- `/api/rank/top10`
- `/api/admin/seckill/activities/{activityId}/metrics`
