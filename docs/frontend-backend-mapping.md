# 前端 ↔ 后端 接口映射（事实来源：后端真实代码 + 本机联调实测）

> 本文只写**在代码里核对过、并且在本机跑通**的接口；未使用/不可用的接口在图末单列。
> 修订时间：2026-10-17 ｜ 对应前端版本：`frontend/`（Vite + React 18 + TS）

## 1. 运行时与请求路径

| 项 | 值 | 说明 |
|---|---|---|
| 前端开发地址 | `http://127.0.0.1:5173` | `vite dev` |
| 前端预览地址 | `http://127.0.0.1:4173` | `vite preview`（同样配置了代理） |
| 浏览器请求基址 | 同源（`''`） | **禁止直连** `8090`：会话 Cookie 是 `SameSite=Strict`，跨端口不携带 |
| 代理目标 | `http://127.0.0.1:8090`（网关） | `VITE_PROXY_TARGET` 可覆盖 |
| 代理的前缀 | `/api`、`/uploads` | `/uploads` 是商品图片静态资源，同样必须经网关 |

## 2. 认证与安全

| 项 | 实现 | 前端位置 |
|---|---|---|
| 会话 | 服务端 `Set-Cookie`（`FLASH_SESSION`，HttpOnly） | 由浏览器自动携带，`fetch` 固定 `credentials: 'include'`（`lib/http.ts`） |
| CSRF | 写操作需 `X-CSRF-Token`，值 = 登录响应 `data.csrfToken` 或 `GET /api/auth/csrf` | `lib/http.ts` 内存保存；`40301` 时自动取新 token 并**重放一次** |
| 恢复登录态 | `GET /api/auth/me`（未登录返回 `40100`） | `stores/auth.tsx` 启动即请求，`40100` 视为未登录 |
| 会话失效 | 任意接口返回 `40100` | `lib/http.ts` 触发 `onUnauthorized` → 清空用户态 → `RequireAuth` 跳登录并带回跳地址 |
| 管理端 | 仅需 `X-Admin-Token`（无 CSRF） | 令牌由页面表单输入，只存 `sessionStorage`（`stores/adminToken.tsx`） |

## 3. 用户端接口映射

| 前端模块 | 方法 / 路径 | 认证 | 请求 | 响应关键字段 | 代码位置 |
|---|---|---|---|---|---|
| 商品列表 + 搜索 + 分页 | `GET /api/products?page&pageSize&keyword` | 公开 | `page≥1`、`pageSize∈[1,100]`、`keyword` 可空 | `{records,page,pageSize,total,pages}`，`records[].productId/name/imageUrl/originalPrice/availableStock/totalStock/status` | `api/product.ts`、`pages/ProductListPage.tsx` |
| 商品详情 | `GET /api/products/{productId}` | 公开 | — | `ProductVO` | `api/product.ts`、`pages/ProductDetailPage.tsx` |
| 活动列表 | `GET /api/seckill/activities` | 公开 | — | `ActivityItemVO[]`：`activityId/productId/productName/productImage/startTime/endTime/seckillPrice/originalPrice/status/stock/totalStock/limitPerUser/preheated` | `api/activity.ts`、`pages/ActivityListPage.tsx` |
| 活动详情 | `GET /api/seckill/activities/{activityId}` | 公开 | — | `SeckillActivityVO`（**无 `preheated`**，`stock === null` 即未预热） | `api/activity.ts`、`pages/ActivityDetailPage.tsx` |
| 秒杀提交 | `POST /api/seckill/execute` | 登录 + CSRF | `{activityId, productId?}` | `{activityId,userId,result,orderNo}`；`result ∈ QUEUED/SOLD_OUT/DUPLICATED/RATE_LIMITED/REJECTED` | `api/seckill.ts`、`components/seckill/SeckillPanel.tsx` |
| 订单轮询 | `GET /api/seckill/orders/{orderNo}` | 登录（校验归属） | — | 订单详情（含 `status`、`seckillPrice`、`createdAt`） | `api/order.ts`、`hooks/queries/useOrders.ts` |
| 我的订单 | `GET /api/seckill/users/me/orders?activityId=` | 登录 | `activityId` 可选 | `OrderListItemVO[]`：`orderNo/activityId/productId/status/createdAt` | `api/order.ts`、`pages/OrdersPage.tsx` |
| 抢购榜 | `GET /api/rank/top10?activityId=` | 公开 | — | `[{rank,userId,orderNo}]` | `api/rank.ts`、`components/seckill/RankList.tsx` |
| 注册 | `POST /api/auth/register` | 公开 | `{accountName,password,displayName?}` | `{user, csrfToken}`（**字段是 `user`**） | `api/auth.ts`、`pages/RegisterPage.tsx` |
| 登录 | `POST /api/auth/login` | 公开 | `{accountName,password}` | 同上 | `api/auth.ts`、`pages/LoginPage.tsx` |
| 当前用户 | `GET /api/auth/me` | 登录 | — | `{id,accountName,displayName,status,createdAt}` | `stores/auth.tsx`、`pages/ProfilePage.tsx` |
| 取 CSRF | `GET /api/auth/csrf` | 登录 | — | `string` | `api/auth.ts`（仅 `http` 层兜底调用） |
| 登出 | `POST /api/auth/logout` | 登录 | — | `null` | `api/auth.ts`、`TopNav`/`ProfilePage` |

## 4. 管理端接口映射（`X-Admin-Token`）

| 前端模块 | 方法 / 路径 | 请求 | 响应 | 代码位置 |
|---|---|---|---|---|
| 商品新建/更新 | `POST /api/admin/products` | `ProductRequest{productId?,name,description?,imageUrl?,originalPrice(分),totalStock,status?}` | `{productId,...}` | `api/admin.ts`、`pages/admin/AdminProductPage.tsx` |
| 商品详情 | `GET /api/admin/products/{id}` | — | `ProductVO` | `api/admin.ts`（已封装，页面未用） |
| 图片上传 | `POST /api/admin/files/image` | `multipart/form-data`，字段名 **`file`** | `{url, storageType}` | `api/admin.ts`、`components/admin/ImageUploader.tsx` |
| 活动创建 | `POST /api/admin/seckill/activities` | `ActivityRequest{productId,activityName,startTime,endTime,seckillPrice,seckillStock,limitPerUser}` | `{activityId,...}` | `api/admin.ts`、`pages/admin/AdminActivityPage.tsx` |
| 预热库存 | `POST /api/admin/seckill/activities/{id}/preheat` | — | `Result` | 同上 |
| 重置库存 | `POST /api/admin/seckill/activities/{id}/reset-stock` | — | `Result` | 同上 |
| 实时指标 | `GET /api/admin/seckill/activities/{id}/metrics` | — | `{activityId,redisStock,orderCount,queuedMessageCount,successCount,duplicateRejectCount,rateLimitRejectCount,soldOutRejectCount}` | `components/admin/MetricsPanel.tsx`（5s 自动刷新） |
| 指标快照 | `POST /api/admin/seckill/activities/{id}/snapshot` | — | `Result` | `pages/admin/AdminDashboardPage.tsx` |
| 死信批量重放 | `POST /api/admin/seckill/dead-letters/replay-batch` | `{originalMessageIds:[...]}` | `{attempted,succeeded,failed,failures[]}` | `components/admin/DeadLetterPanel.tsx` |
| 死信单条重放 | `POST /api/admin/seckill/dead-letters/replay` | `{originalMessageId}` | `{originalMessageId,replayMessageId}` | `api/admin.ts`（已封装，页面用批量） |

## 5. 枚举真实值（联调时逐条核对）

| 枚举 | 真实值 | 前端如何用 |
|---|---|---|
| `ProductStatusEnum` | `OFF_SHELF` / `ON_SHELF` | 详情页徽章；列表接口只返回上架商品 |
| `ActivityStatusEnum` | `NOT_STARTED` / `RUNNING` / `ENDED` / `SOLD_OUT` / `CANCELLED` | **仅作参考**：后端 `status` 是创建时快照、不会随时间自动翻转 ⇒ 阶段以时间窗优先（`lib/activityPhase.ts`） |
| `OrderStatusEnum` | `QUEUING` / `CREATED` / `CREATE_FAILED` / `CANCELLED` | `CREATED` 即成功终态（无支付环节）；`QUEUING` 才轮询 |
| 秒杀 `result` | `QUEUED` / `SOLD_OUT` / `DUPLICATED` / `RATE_LIMITED` / `REJECTED` | 入队是 **`QUEUED`**（不是 QUEUING） |

错误码：`0` 成功、`40100` 未登录、`40101` 账号或密码错误、`40301` CSRF 失效、`40901` 重复秒杀、`40902` 售罄、`42900` 限流、`40001` 业务参数错误、`50000` 系统/依赖异常（映射见 `lib/errors.ts`）。

## 6. 联调中发现并已修正的契约偏差（5 条，全部有实测）

| # | 我最初的写法（假设） | 真实后端 | 修正 |
|---|---|---|---|
| 1 | 商品状态 `ON_SALE` | `ON_SHELF` | `types/product.ts` + 详情徽章 + 管理端下拉 |
| 2 | 活动状态 `IN_PROGRESS` | `RUNNING` | `types/activity.ts`；同时区分"前端展示阶段" |
| 3 | 秒杀入队结果 `QUEUING` | **`QUEUED`** | `types/activity.ts` + `SeckillPanel` 判定 |
| 4 | 订单成功态 `SUCCESS` | **`CREATED`**（无支付环节） | `types/order.ts` + 终态集合 + 徽章文案 |
| 5 | 活动时间用 ISO `2026-10-17T21:09` | `@JsonFormat("yyyy-MM-dd HH:mm:ss")`（空格分隔） | `AdminActivityPage.toServerTime` |

**第 6 条（设计层面）**：`activity.status` 不随时间翻转。若前端直接用 `status === 'NOT_STARTED'` 禁用按钮，活动已开始也永远点不了 ⇒ 阶段判定改为**时间窗优先**，`status` 仅作补充（`lib/activityPhase.ts`，含单测）。

## 7. 有意未使用 / 不可用

| 接口 | 原因 |
|---|---|
| `/internal/**` | 集群内部接口，需内部令牌，前端禁止调用 |
| `POST /api/seckill/activities/{id}/check` | 已封装 `checkEligibility`，但返回体是未固定结构的 `Map`，页面不臆造字段；资格判断改由「真实 `execute` 的 `result` 枚举 + 订单状态」表达 |
| `GET /api/seckill/users/{userId}/orders` | 与 `me` 语义重复，`me` 已满足需求 |
| `POST /api/admin/seckill/dead-letters/replay`（单条） | 页面用批量端点即可覆盖 |

## 8. 联调前置条件（否则订单永远排队）

```
FLASH_STREAM_AUTO_POLL=true     # StreamConsumerScheduler 默认关闭；不开启则无人建单、订单停在 QUEUING、榜单不更新
```
