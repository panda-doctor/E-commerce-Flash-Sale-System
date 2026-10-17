# 前端功能审查清单（现有工程增量优化）

审查对象：`frontend/`（React 18 + TS strict + Vite 5 + React Query）
审查方式：逐文件阅读实现 + 沿调用链追踪 + 对照后端 Controller/DTO 真实契约 + 真后端联调（5173 同源代理）
审查时间：2026-10-17

状态图例：`✅ 已实现且验证通过`｜`⚠️ 已实现但有缺陷`｜`◐ 部分实现`｜`✗ 尚未实现`｜`⊘ 受后端/环境限制`

## 一、总览

| 模块 | 状态 | 结论 |
|---|---|---|
| 请求层（信封 / 大整数 / CSRF / 40100） | ✅ | 与后端 `Result{code,message,data,requestId}` 一致；`40301` 自动换 token 重放；`40100` 统一登出 |
| 路由与懒加载 / 刷新回退 | ✅ | 10 条路由实测 200，未登录由 `RequireAuth` 拦截并带回跳 |
| 商品列表（搜索 / 分页 / 三态 / 图片兜底） | ⚠️ | 主链路正确；**页码越界无处理**、筛选切换会短暂展示旧页数据 |
| 商品详情 | ✅ | 按真实 `productId` 加载，缺失/失败走 `ErrorState` |
| 活动阶段判定 | ✅ | 已修正为**时间窗优先**（后端 `status` 是创建时快照，不自动翻转） |
| 倒计时 | ⚠️ | 归零后因 `onFinish` 引用变化导致 **每次渲染都 refetch**（自激循环） |
| 秒杀提交与结果反馈 | ✅ | `result` 按真实枚举 `QUEUED/SOLD_OUT/DUPLICATED/RATE_LIMITED/REJECTED` 处理 |
| 秒杀终态刷新 | ⚠️ | `invalidateQueries` 写在**渲染体**；且只失效 `['orders']`，漏 `activity/activities/rank` |
| 订单轮询 | ⚠️ | 终态判定正确；但**无次数/时长上限**、连续失败不熔断 |
| 用户数据隔离 | ⚠️ | 订单 queryKey 不含用户标识；40100 只清 `me`，未清订单缓存 ⇒ 可能闪现上一用户数据 |
| 登录 / 注册 / 个人中心 | ⚠️ | 主流程可用；注册表单未对齐后端 `@Size`（`displayName ≤ 64`、密码 10~72） |
| 登出 | ⚠️ | 登出请求失败时仅 `console.error`，界面仍显示已登录态，用户误以为已退出 |
| 我的订单 / 订单详情 | ✅ | 真实接口、状态映射、空/错/加载态齐全；轮询上限同上 |
| 管理端（令牌 / 上传 / 指标 / 快照 / 死信） | ✅ | 令牌闸门、multipart 上传、5s 指标刷新、批量重放均已实测 |
| 全局异常兜底 | ✗ | 无 `ErrorBoundary`，任一渲染异常导致整页白屏 |
| 移动端导航 | ◐ | `TopNav` 的导航 `<md` 隐藏，手机端无任何入口 |
| Toast | ⚠️ | 无条数上限，连续错误会堆叠遮挡 |
| 错误可诊断性 | ⚠️ | 后端 `Result.requestId` 未透出到错误态 |
| 工程门禁 | ✅ | `tsc` / `eslint` / `vitest` / `vite build` 全绿 |

## 二、确认缺陷（本轮必修）

### D1. 秒杀面板在渲染期执行缓存失效

- 位置：`src/components/seckill/SeckillPanel.tsx:72-75`
- 现状：`if (orderStatus && orderStatus !== 'QUEUING') { queryClient.invalidateQueries({ queryKey: ['orders'] }) }` 写在组件函数体中。
- 根因：把副作用当作派生逻辑写在渲染路径上（React 渲染必须纯）。
- 影响：每次渲染都触发一次缓存失效 → 订单列表反复重取；数据变化引起的重渲染会再次触发，存在刷新循环与"渲染期间更新"告警；同时注释声称刷新榜单，实际漏了 `activity / activities / rank`，导致秒杀成功后库存与榜单展示陈旧。
- 方案：移入 `useEffect`，用 `handledRef` 记录已处理的 `(orderNo,status)` 组合，保证每个终态只失效一次；失效键统一走 `queryKeys` 工厂，一次覆盖 `orders / order / activity / activities / rank`。
- 验证：单测断言"终态只失效一次、队列中不失效"；真后端秒杀后榜单与库存即时更新。

### D2. 倒计时归零后自激刷新

- 位置：`src/components/activity/Countdown.tsx:31-36`，调用点 `src/pages/ActivityDetailPage.tsx:121`（内联箭头 `onFinish={() => activityQuery.refetch()}`）
- 根因：`useEffect` 依赖包含 `onFinish`，而该函数每次渲染都是新引用；`finished` 一旦为 `true`，effect 每次渲染都重新执行 → `refetch()` → 新数据 → 重渲染 → 再 refetch。
- 影响：活动结束时刻起持续网络请求，占用连接、可能引发接口雪崩。
- 方案：`onFinish` 存入 `useRef` 并在渲染后同步；`firedRef` 保证"归零只回调一次"，`target`/`finished` 复位时重置标记。
- 验证：假定时器单测——归零后多次重渲染，`onFinish` 仅调用 1 次。

### D3. 订单轮询无上限、失败不熔断

- 位置：`src/hooks/queries/useOrders.ts:46-62`（`useOrderPolling`）、`:29`（`useMyOrders`）
- 现状：`refetchInterval` 只判断终态与页面可见性，没有最大时长/最大失败次数。
- 影响：后端长期 `QUEUING`（消费者未开）或查询持续报错时，页面无限轮询。
- 方案：`useOrderPolling` 增加 `intervalMs / maxDurationMs / maxFailures`（默认 1.5s / 90s / 5 次），超时或连续失败即返回 `false` 停止轮询，并对外暴露 `pollingStopped`；`useMyOrders` 同样加时长上限与失败熔断。保留原两参调用形式（向后兼容）。
- 验证：单测覆盖"到时长上限停 / 连续失败停 / 终态停 / 隐藏页停"。

### D4. 用户域缓存未隔离

- 位置：`src/hooks/queries/useOrders.ts:29,50`（键 `['orders','me',…]`、`['order',orderNo]`）、`src/stores/auth.tsx:55-69`
- 现状：queryKey 里没有真实用户标识；`logout()` 只 `removeQueries(['orders'])`，40100 回调只把 `me` 置空，`login()` 不清理。
- 影响：会话过期后同标签页换账号登录，可能瞬时看到上一用户的订单列表/订单详情（缓存命中后先渲染旧数据）。
- 方案：新增 `src/lib/queryKeys.ts` 统一键工厂，`orders(userId,…)` / `order(userId,…)` 键内带 `userId`；`AuthProvider` 在 `login / register / logout / 40100` 四个入口统一 `removeQueries` 清理用户域缓存。
- 验证：单测断言不同 userId 生成不同键；真后端切换账号后列表不残留。

## 三、补充发现（本轮一并处理）

| # | 位置 | 问题 | 处理 |
|---|---|---|---|
| S1 | 全工程 | 无 `ErrorBoundary`，渲染异常白屏 | 新增 `components/system/ErrorBoundary.tsx` 包裹路由内容，提供"重试 / 回首页" |
| S2 | `src/lib/http.ts` | 未透出 `Result.requestId` | `ApiError` 增加 `requestId`，错误态展示可复制的请求号 |
| S3 | `src/pages/ProductListPage.tsx` | 页码越界（后端返回空 `records` 但 `total>0`）无处理；筛选切换沿用旧页数据 | 越界自动回退并提供提示；条件变化时不保留旧页数据 |
| S4 | `src/components/layout/TopNav.tsx` | `<md` 无导航入口 | 增加抽屉导航（Radix Dialog） |
| S5 | `src/components/ui/Toast.tsx` | 无条数上限 | 仅保留最近 3 条 |
| S6 | `src/pages/RegisterPage.tsx` | 未校验 `displayName ≤ 64`、密码 10~72、首尾空格 | 对齐后端 `@Size` 约束（不臆造格式规则） |
| S7 | `src/components/layout/TopNav.tsx:25`、`src/pages/ProfilePage.tsx:25` | 登出失败静默，界面仍显示登录态 | 失败时 toast 提示并保留登录态语义 |
| S8 | `src/pages/ProductDetailPage.tsx:47` | 数据未就绪时 `return null` 造成短暂空白 | 改为骨架占位 |
| S9 | `src/components/admin/MetricsPanel.tsx:45` | 指标键不含管理员令牌，切换令牌复用旧缓存 | 键中加入令牌指纹并 `enabled` 守卫 |

## 四、后端限制（不修改后端）

| 项 | 说明 |
|---|---|
| 商品列表排序 | 后端 `GET /api/products` 仅支持 `page/pageSize/keyword`，无排序/状态筛选参数 ⇒ 不做假筛选 |
| 活动列表分页 | 后端一次性返回全部活动且无分页参数 ⇒ 前端不分页 |
| 取消订单 / 支付 | 后端无对应能力 ⇒ 前端不提供相关按钮 |
| 活动 `status` 快照 | 后端不随时间的自动翻转 ⇒ 前端按时间窗判定（本项目既有实现，保持不变） |
| 秒杀资格查询 | `POST /activities/{id}/check` 返回结构未定型 ⇒ 不臆造字段，资格以 `execute` 的 `result` 表达 |

## 五、验证计划与结果

| 项 | 命令 / 方式 | 结果 |
|---|---|---|
| 类型检查 | `npx tsc --noEmit -p tsconfig.app.json` | ✅ exit 0，0 error |
| 静态检查 | `npm run lint` | ✅ exit 0，0 error（5 条既有 `react-refresh` 提示） |
| 单元测试 | `npx vitest run` | ✅ **52 passed / 10 files**（新增 15 条） |
| 生产构建 | `npm run build` | ✅ `built in 5.56s`，分包正常 |
| 真后端回归 | 经 `5173` 代理 + `curl` | ✅ 14/16 场景实测通过；2 条如实标注未验证（见第六节） |

## 六、修复与复检结果

| 编号 | 修复内容 | 验证证据 |
|---|---|---|
| D1 | 秒杀终态刷新移入 `useEffect` + `handledRef` 去重；一次性失效 `orders/order/activity/activities/rank` | 回归：秒杀后指标 `orderCount=1 redisStock=2`、订单列表即时可见；代码中渲染体已无 `queryClient` 调用 |
| D2 | `Countdown` 用 `ref` 固化回调 + `firedRef` 只触发一次 | 单测：多次重渲染后 `onFinish` 仍为 **1 次** |
| D3 | 轮询加 `maxDurationMs=90s` / `maxFailures=5` 熔断，暴露 `pollingStopped`；订单页/详情页提供手动刷新 | 单测 `polling.test.ts` 6 条（终态/隐藏/失败/超时/首帧）；回归：排队单第 1 次轮询即 `CREATED` 并停止 |
| D4 | 新增 `lib/queryKeys.ts`，订单键带 `userId`；`login/register/logout/40100` 统一 `clearUserScopedCache` | 单测：不同 userId 键不同、清缓存不影响商品缓存；回归：登出后 `me=40100` |
| S1 | 新增 `ErrorBoundary` 包裹路由内容，提供重试/回首页 | 单测：子组件抛错渲染兜底 UI |
| S2 | `ApiError.requestId` + `ErrorState` 展示请求号 | 实网：商品不存在返回 `code=40004` 且带 `requestId=202610172207440100` |
| S3 | 页码越界自动回退并提示；搜索切换不复用旧页数据 | 回归：`page=2` 返回 `data.page=2` 且与第 1 页不同；代码审阅确认 `placeholderData` 仅在关键词相同时保留 |
| S4 | `TopNav` 增加 `<md` 抽屉导航（Radix Dialog） | 代码审阅 + dev server 转译 200；移动端入口含商品/活动/订单/管理/登录 |
| S5 | Toast 仅保留最近 3 条 | 代码审阅（`slice(-MAX_VISIBLE)`） |
| S6 | 注册校验对齐后端：账号 `^[a-z0-9._-]{3,32}$`、密码 10~72、昵称 ≤64 | 已核对 `RegisterRequest` 注解（原占位文案「4~32」已纠正） |
| S7 | 登出失败不再静默：`logout()` 返回服务端确认结果，失败时明确提示 | 代码审阅；`logout` 仍清理本地态（保证界面不残留登录态） |
| S8 | 商品详情未就绪时的空白改为骨架（既有实现复核为 `ErrorState`/骨架分支） | 代码审阅 |
| S9 | 指标缓存键加入**不可逆令牌指纹**，无令牌时不请求 | 单测：指纹不含原文且可区分不同令牌 |

### 未验证项（如实记录）

| 场景 | 原因 |
|---|---|
| 接口请求失败 → 反馈与重试 | ✅ **第二轮已用真实 Chrome 验证**：`route.abort()` 拦截 `/api/products`，页面出现错误态与「重新加载」按钮，未白屏 |
| 前端防重复点击的**视觉**验证 | 仍未验证：点击"立即秒杀"会产生真实订单/扣库存，不对生产数据做该操作；逻辑上 `mutation.isPending` 已禁用按钮，后端幂等实测 `40901` |

## 七、第二轮复检：真实浏览器实测新增缺陷（已全部修复）

首轮只能用 curl + jsdom 验证，无法覆盖浏览器运行期。第二轮用系统 Chrome（headless）+ `scripts/browser-check.mjs` 采集 `console`/`pageerror`/`requestfailed`/响应码，直接抓到了两个**只在浏览器里才暴露**的缺陷。

| 编号 | 现象（浏览器证据） | 根因 | 修复 | 验证 |
|---|---|---|---|---|
| D5 | `/admin`、`/admin/products`、`/admin/activities` 整页变成「页面出现异常」；`pageerror: useAdminToken 必须在 <AdminTokenProvider> 内使用` | `AdminTokenProvider` 挂在 `AdminShell` **内部**，而三个管理页在组件顶部调用 `useAdminToken()`，首次渲染时 hook 运行在 Provider 之外 | Provider 上移到路由层 `AdminLayout`，`AdminShell` 只保留令牌闸门 | 真实 Chrome：`/admin` 展示「需要管理令牌」，`pageErrors=0`；载入令牌后指标面板正常；新增路由级回归测试 3 条 |
| D6a | 管理控制台点击「快速选择」后 `TypeError: activityId.trim is not a function`，指标不渲染 | 后端 13~15 位 `Long` 主键仍是 JSON number（`90295171892224`），`setActivityId(number)` 写入 string 状态 | 新增 `lib/id.ts:toId`，API 边界统一归一化；页面再加防御转换 | 真实 Chrome：选活动 → 指标 8 项渲染，`pageErrors=0` |
| D6b | 商品详情页**永远**显示「该商品当前没有进行中的秒杀活动」 | 路由参数 `productId` 为 string，接口返回 `productId` 为 number，`===` 恒为 false | 同 D6a（`api/product.ts`、`api/activity.ts` 归一化） | 真实 Chrome：`/products/90173199893504` 显示「有秒杀活动」+「去秒杀」链接 |

**根因归纳**：`http` 层只把 **≥16 位**整数转字符串（防精度丢失），13~15 位 ID 未覆盖；而 `Id` 类型声明为 `string`，导致运行期类型与声明不符，进而出现 `.trim()` 崩溃、`===` 恒 false、queryKey 数字/字符串两套缓存。现已在 API 边界统一收口（订单、活动、商品、榜单四个模块）。

### 第二轮真实浏览器验证结果

| 场景 | 结果 |
|---|---|
| 首页商品列表 | `共 10 件商品`，卡片与详情链接正常 |
| 搜索 / 清空 | `共 1 件` → `共 10 件` |
| 活动详情 | 标题、价格、`抢购榜 TOP 10`、按钮「未开始」且禁用（时间窗判定生效） |
| 商品详情关联活动 | 「有秒杀活动」徽章 + 「去秒杀」链接（D6b 修复后） |
| 未登录访问 `/orders` | 跳转 `/login`（带回跳） |
| 登录 | 跳回 `/orders`，导航显示用户（`navShowsUser=true`） |
| 我的订单 / 订单详情 | 4 条订单，详情页 0 新增错误 |
| `/admin`（无令牌） | 令牌闸门表单，无异常 |
| `/admin`（有令牌） | 「已载入管理令牌」+ 指标面板（Redis 库存/成交/售罄拒绝/重复拒绝/限流拒绝） |
| 移动端 390×844 | 汉堡菜单可见，抽屉含 5 个链接 |
| 无效路由 | 「页面走丢了」正常兜底 |
| 接口失败（`route.abort`） | 错误态 + 「重新加载」按钮，未白屏 |
| 控制台/网络 | `pageErrors=0`；无失败请求；仅 **4 张种子数据图片 404**（见下） |

### 仍未修复：种子商品图片 404（后端数据问题，非前端缺陷）

浏览器实测 4 个 URL 形如 `/uploads/202609/xxxx.png` 返回 404。核对磁盘：`uploads/` 下只有 3 个 `202610` 文件，`202609` 目录不存在 —— 数据库里种子商品的 `image_url` 指向了从未落盘的文件。

- 前端行为正确：`ProductImage` 的 `onError` 已降级为「暂无图片」占位块，页面不裂版、不报 JS 异常；
- 修复该问题需要**后端补文件或修正种子数据**，不属于前端可修范围，本次不改数据库与后端规则；
- 影响：控制台有 4 条 404 日志，商品卡片无图（占位显示）。
