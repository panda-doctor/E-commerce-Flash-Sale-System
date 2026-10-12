# 轻量级电商秒杀系统接口文档

## 一、文档目标

本文档用于约定项目后续开发中的接口边界。接口设计围绕项目核心方向展开：商品缓存、秒杀活动、库存预热、限流防刷、原子扣减、异步下单、订单查询和实时排行榜。

当前接口按完整目标设计，实际开发可按阶段逐步实现。

## 二、基础约定

### 2.1 基础地址

本地开发默认地址：

```text
http://localhost:8081
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
| `40100` | 未授权访问（未登录、会话失效或管理令牌无效） |
| `40101` | 账号名或密码错误（统一提示） |
| `40901` | 重复秒杀 |
| `40902` | 库存不足 |
| `40903` | 旧动态用户标识冲突码（公开注册已停用） |
| `40904` | 账号名已被使用 |
| `40301` | 防跨站请求校验失败 |
| `42900` | 请求过于频繁 |
| `50000` | 系统异常 |
| `50300` | AI 服务未配置（缺 `ai.llm.api-key` / `ai.llm.base-url`） |
| `50301` | AI 服务调用失败（网络 / 超时 / 大模型返回异常） |

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
| 商城商品 | `GET` | `/api/products?page=1&pageSize=20&keyword=` | 分页搜索仅上架商品 |
| 管理端商品详情 | `GET` | `/api/admin/products/{productId}` | 按编号载入商品（包括下架商品，需管理令牌） |
| 管理端商品 | `POST` | `/api/admin/products` | 创建或更新商品 |
| 管理端活动 | `POST` | `/api/admin/seckill/activities` | 创建秒杀活动 |
| 管理端预热 | `POST` | `/api/admin/seckill/activities/{activityId}/preheat` | 预热活动和库存到缓存 |
| 管理端库存重置 | `POST` | `/api/admin/seckill/activities/{activityId}/reset-stock` | 重置活动库存（仅未开始/已结束，运维/压测复位） |
| 活动广场 | `GET` | `/api/seckill/activities` | 活动广场列表（含商品快照、实时库存、预热标记） |
| 秒杀活动 | `GET` | `/api/seckill/activities/{activityId}` | 查询活动详情 |
| 秒杀校验 | `GET` | `/api/seckill/activities/{activityId}/check` | 查询用户是否可参与 |
| 秒杀执行 | `POST` | `/api/seckill/execute` | 执行秒杀请求 |
| 订单查询 | `GET` | `/api/seckill/orders/{orderNo}` | 查询订单状态 |
| 用户订单 | `GET` | `/api/seckill/users/{userId}/orders` | 查询用户秒杀订单 |
| 我的订单 | `GET` | `/api/seckill/users/me/orders` | 按当前登录会话查询订单 |
| 排行榜 | `GET` | `/api/rank/top10` | 查询秒杀成功排行榜 |
| 管理端指标 | `GET` | `/api/admin/seckill/activities/{activityId}/metrics` | 查询活动运行指标 |
| 管理端快照 | `POST` | `/api/admin/seckill/activities/{activityId}/snapshot` | 手动打点活动指标快照（压测/复盘用） |
| 死信回放 | `POST` | `/api/admin/seckill/dead-letters/replay` | 人工回放死信消息（需管理令牌） |
| 图片上传 | `POST` | `/api/admin/files/image` | 上传商品图片（multipart，本地磁盘 / OSS 双策略） |
| AI 客服 | `POST` | `/api/support/chat` | AI 客服对话（接入外部 OpenAI 兼容大模型） |
| 用户账户 | `POST` | `/api/auth/register` | 账号名、密码和可选昵称注册并创建会话 |
| 用户账户 | `POST` | `/api/auth/login` | 账号密码登录并创建会话 |
| 用户账户 | `GET` | `/api/auth/me` | 查询当前用户资料 |
| 用户账户 | `POST` | `/api/auth/logout` | 撤销当前会话 |
| 管理端用户 | `GET` | `/api/admin/users` | 分页查询、搜索与状态筛选 |
| 管理端用户 | `GET/POST/PUT/DELETE` | `/api/admin/users/{userId}` | 管理用户资料、状态、密码及软删除 |

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

用途：查询上架商品信息，使用缓存旁路模式。下架商品通过该公开接口返回 `40004`；管理员应使用 `GET /api/admin/products/{productId}` 查询，包括下架商品。

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

### 4.2.1 商城商品列表

```text
GET /api/products?page=1&pageSize=20&keyword=键盘
```

只返回已上架商品，按最近更新时间倒序。`page` 默认 `1`，`pageSize` 默认 `20`、最大 `100`，`keyword` 按商品名称搜索。`data` 使用通用分页结构：`records`、`page`、`pageSize`、`total`、`pages`；`records` 中每项包含商品编号、名称、描述、图片地址、原价（分）、库存、状态和缓存命中标记。

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

用途：创建秒杀活动，配置商品、秒杀价、库存、开始时间和结束时间。

请求体：

```json
{
  "activityId": 1,
  "productId": 1,
  "activityName": "键盘限时秒杀",
  "startTime": "2026-07-27 20:00:00",
  "endTime": "2026-07-27 21:00:00",
  "seckillPrice": 9900,
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

预热与库存重置共用活动缓存写锁 `seckill:lock:activity-cache:{activityId}`，避免并发覆盖同一库存键。缓存过期时间统一为活动结束时间加缓冲；剩余时间不足时至少保留 60 秒。活动列表仅在活动信息 Hash 与库存键都存在时返回 `preheated=true`，仅有库存键属于缓存不完整。

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

用途：检查活动当前是否可参与（活动不存在返回 `40004`）。判定口径与 `execute` 一致：
显式取消 → 未开始 / 已结束 → 实时库存售罄 → 未预热；结果通过 `canJoin` + `reason` 表达
（`ALLOW` / `ACTIVITY_CANCELLED` / `ACTIVITY_NOT_STARTED` / `ACTIVITY_ENDED` /
`ACTIVITY_SOLD_OUT` / `ACTIVITY_NOT_PREHEATED`）。用户是否重复秒杀、是否触发限流由
`execute` 落地（返回 `40901` / `42900`），本接口不预判。

身份由登录会话解析，`userId` 查询参数可省略。为兼容旧请求，如传入该参数，必须与当前登录用户一致，否则返回 `40100`。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | 数字 | 否 | 兼容旧客户端使用；不得与会话用户不一致 |

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
{ "activityId": 1, "productId": 1 }
```

秒杀执行使用登录会话中的用户编号。旧 `userId` 字段可省略；若提供，必须与会话用户一致。会话写请求还需携带 `X-CSRF-Token`。

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

用途：秒杀请求入队后，前端可轮询此接口查询订单最终状态。必须使用有效登录会话；订单存在时服务端校验订单归属。

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
GET /api/seckill/users/me/orders?activityId=1
```

用途：根据登录会话查询当前用户在指定活动下的订单。兼容旧路径 `/users/{userId}/orders`，但路径编号必须与会话用户一致。

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

### 4.13 AI 客服对话

```text
POST /api/support/chat
```

用途：AI 购物助手对话。后端将「系统人设 + 实时商品/秒杀场次目录 + 前端带回的最近历史」组装后调用
外部 OpenAI 兼容大模型（阿里云百炼兼容模式 / DeepSeek / OpenAI 等），返回文本回复。

请求示例：

```json
{
  "message": "今天有什么秒杀？",
  "history": [
    { "role": "user", "content": "你好" },
    { "role": "assistant", "content": "我在的，有什么可以帮你？" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `message` | string | 是 | 用户问题，≤500 字 |
| `history` | array | 否 | 最近对话上下文（时间正序），后端最多取最近 10 条、单条 ≤600 字 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "reply": "目前有机械键盘 Pro 正在秒杀，10:00 场还剩 9 件，先到先得～"
  },
  "requestId": "202607271200001016",
  "timestamp": 1785124800000
}
```

说明：

- 无登录体系、服务端不落会话：前端在本地维护消息，把最近几轮随请求带回即可续上下文。
- 需在后端配置 `ai.llm.base-url` 与 `ai.llm.api-key`（支持 `AI_LLM_API_KEY` 环境变量），否则返回 `50300`。
- 大模型调用失败（网络/超时/非 2xx/响应异常）返回 `50301`，回复仅供参考。

### 4.14 重置秒杀活动库存

```text
POST /api/admin/seckill/activities/{activityId}/reset-stock
```

用途：运维 / 压测复位——将活动库存恢复为数据库配置值，刷新库存缓存键的 TTL。

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `activityId` | 数字 | 是 | 活动编号 |

语义约束（E1-r）：

- 仅允许「未开始」或「已结束」的活动重置库存（压测前复位、修复缓存库存异常）。
- 活动**进行中**禁止重置，由服务端守卫拦截并返回业务错误，防止把 Redis 已扣减的库存用数据库配置库存"复活"。
- 活动不存在返回 `40004`。
- 重置只修改库存缓存键，不会重写活动信息 Hash；库存键设置活动结束时间加缓冲的 TTL，最短 60 秒。列表只有在活动信息 Hash 与库存键同时存在时才报告已预热。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "activityId": 1,
    "stockKey": "seckill:stock:1",
    "stock": 100
  },
  "requestId": "202609081200001017",
  "timestamp": 1785124800000
}
```

### 4.15 用户注册、登录与会话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册账号并自动登录 |
| `POST` | `/api/auth/login` | 使用账号名和密码登录 |
| `GET` | `/api/auth/me` | 查询当前账号资料 |
| `GET` | `/api/auth/csrf` | 轮换并返回当前会话的防跨站请求令牌 |
| `POST` | `/api/auth/logout` | 撤销当前会话并清除 Cookie |

注册请求字段：`accountName`（3 至 32 位字母、数字、点、下划线或连字符，大小写不敏感）、`password`（至少 10 个字符且 UTF-8 不超过 72 字节）、可选 `displayName`（最多 64 字符）。不收集手机号或邮箱。重复账号名返回 `40904`；登录失败统一返回 `40101`。

注册与登录成功的 `data` 含 `user`（`id`、`accountName`、`displayName`、`status`、`createdAt`）和 `csrfToken`。响应设置 `FLASH_SESSION`（`HttpOnly; SameSite=Strict`）及 `FLASH_CSRF` Cookie。后续浏览器请求需携带凭据；会话写操作同时发送 `X-CSRF-Token` 请求头和同值的 `FLASH_CSRF` Cookie。刷新页面后先调用 `/api/auth/csrf` 获取新令牌。生产环境须设置 `SESSION_COOKIE_SECURE=true`。

密码使用 BCrypt 单向哈希保存，用户资料响应不包含密码哈希。旧的“任意填写 userId 领取内存令牌”注册契约已停用；秒杀身份由会话确定。旧 `X-User-Token` 压测兼容默认关闭；仅显式设置 `LEGACY_USER_TOKENS_ENABLED=true` 才会读取 `USER_TOKENS`。

### 4.16 管理端用户 CRUD

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/users?page=1&pageSize=20&keyword=&status=1` | 分页、搜索账号名/昵称、按状态筛选 |
| `GET` | `/api/admin/users/{userId}` | 查询用户资料 |
| `POST` | `/api/admin/users` | 创建用户 |
| `PUT` | `/api/admin/users/{userId}` | 更新昵称、状态或重置密码 |
| `DELETE` | `/api/admin/users/{userId}` | 软删除用户 |

以上接口使用现有 `X-Admin-Token`。`status` 仅支持 `0`（禁用）和 `1`（正常），`pageSize` 最大 100。禁用、重置密码及软删除都会撤销用户的全部会话。删除不会清除用户记录或订单。

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
| `rate:limit:{activityId}:{userId}` | 有序集合 | 用户对指定活动的滑动窗口限流（E4：活动×用户双维，避免多活动互相误伤） |
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
- `/api/admin/seckill/activities/{activityId}/reset-stock`
