# 前端业务审查与优化 交付报告

日期：2026-10-17 ｜ 范围：`frontend/`（增量优化，未重建、未重写）｜ 后端：真实多运行时 + 网关（8081/8082/8084/8085/8086/8087/8090 全部在线）

## 一、整体审查结论

前端已具备**完整可用的业务闭环**：商品浏览（真实分页/关键词/三态）→ 商品详情 → 活动与秒杀（真实 `result` 枚举）→ 排队轮询 → 我的订单 → 管理端配置（令牌/上传/预热/指标/死信）。

本次审查以"实际读代码 + 对照后端契约 + 真后端回归"为准，发现并修复 **4 处会导致数据错误或请求异常的缺陷**，补齐 **9 项边界与可靠性能力**。修复后核心链路（下单一轮询一落单）实测通过，工程门禁全绿。

主要风险（修复前）：秒杀面板在渲染期触发缓存失效、倒计时归零后自激刷新、订单轮询无上限、用户订单缓存未按账号隔离。这四项都会在真实使用中表现为"接口被反复打""换账号看到别人订单""页面越用越卡"。

## 二、已修复问题（按业务影响排序）

### 1. 秒杀终态刷新写在渲染路径上（高）

- **修复前**：`SeckillPanel` 组件函数体内直接 `queryClient.invalidateQueries(...)`；且只失效 `['orders']`，注释声称会刷新榜单但实际没有。
- **根因**：把副作用当作派生值写在渲染阶段；React 的渲染必须是纯函数。
- **方案**：移入 `useEffect`，用 `handledRef` 记录 `订单号:状态` 签名，保证同一终态只失效一次；一次失效 `orders / order / activity / activities / rank`。
- **文件**：`src/components/seckill/SeckillPanel.tsx`
- **验证**：真后端秒杀后，订单列表立即可见新单，指标 `orderCount=1 redisStock=2` 与前端展示一致；渲染体已无 `queryClient` 调用。

### 2. 倒计时归零后自激刷新（高）

- **修复前**：`useEffect(..., [finished, onFinish])`，而 `onFinish` 是父组件内联箭头函数，每次渲染都是新引用 ⇒ 归零后每次渲染都回调 → `refetch()` → 重渲染 → 再 `refetch`。
- **方案**：回调存入 `useRef`（渲染后同步），`firedRef` 保证只触发一次，`target` 变化时重置。
- **文件**：`src/components/activity/Countdown.tsx`
- **验证**：`Countdown.test.tsx` 用"多次重渲染 + 内联回调"复现原缺陷，断言 `onFinish` 仅 1 次。

### 3. 订单轮询无上限、失败不熔断（高）

- **修复前**：`refetchInterval` 只判断终态与页面可见性，后端长期 `QUEUING`（消费者未开）或接口持续报错时会一直轮询。
- **方案**：抽出纯函数 `nextPollDelay`（页面隐藏/终态/连续失败 ≥5/时长 ≥90s → 停止），`useOrderPolling` 与 `useMyOrders` 共用；对外暴露 `pollingStopped`，订单列表与详情页展示"自动刷新已停止 + 手动刷新"。
- **文件**：`src/hooks/queries/useOrders.ts`、`src/pages/OrdersPage.tsx`、`src/pages/OrderDetailPage.tsx`、`src/components/seckill/SeckillPanel.tsx`
- **验证**：`polling.test.ts` 6 条用例；回归：排队单第 1 次轮询即 `CREATED` 并停止。

### 4. 用户域缓存未隔离（高，涉及数据边界）

- **修复前**：订单 queryKey 为 `['orders','me',…]` / `['order',orderNo]`，不含用户标识；`logout` 只清 `['orders']`，40100 回调只把 `me` 置空，`login` 不清理 ⇒ 会话失效后同标签页换账号可能先渲染上一用户的订单。
- **方案**：新增 `src/lib/queryKeys.ts` 统一键工厂，`orders(userId,…)` / `order(userId,…)`；`AuthProvider` 在 `login / register / logout / 40100` 统一 `clearUserScopedCache`（只清用户域，保留商品/活动缓存）。
- **文件**：`src/lib/queryKeys.ts`（新）、`src/stores/auth.tsx`、`src/hooks/queries/*`
- **验证**：`queryKeys.test.ts` 断言不同 userId 键不同、清缓存不动公开数据；回归：登出后 `me=40100`。

### 5. 异常与边界（中）

| 问题 | 方案 | 文件 |
|---|---|---|
| 无全局错误边界，渲染异常白屏 | 新增 `ErrorBoundary`（`getDerivedStateFromError` + `componentDidCatch`），提供重试/回首页 | `components/system/ErrorBoundary.tsx`（新）、`App.tsx` |
| 错误信息缺少可追溯编号 | `ApiError.requestId`（读后端 `Result.requestId`），`ErrorState` 展示并支持选中复制 | `lib/http.ts`、`types/api.ts`、`lib/errors.ts`、`ui/ErrorState.tsx` |
| 页码越界无处理，误显示"没有商品" | 自动回退到最后一页并提示 | `pages/ProductListPage.tsx` |
| 搜索条件变化仍展示旧页数据 | `placeholderData` 仅在关键词相同时保留上一页 | `hooks/queries/useProducts.ts` |
| 手机端无导航入口 | `<md` 抽屉菜单（Radix Dialog），含全部主入口与管理端 | `components/layout/TopNav.tsx` |
| Toast 无上限遮挡页面 | 仅保留最近 3 条 | `components/ui/Toast.tsx` |
| 注册校验与后端不一致（提示 4~32 位、缺长度上限） | 对齐 `RegisterRequest`：`^[a-z0-9._-]{3,32}$`、密码 10~72、昵称 ≤64 | `pages/RegisterPage.tsx` |
| 登出失败静默，界面仍显示登录态 | `logout()` 返回服务端确认结果，失败时明确提示（本地态仍清理） | `stores/auth.tsx`、`TopNav.tsx`、`ProfilePage.tsx` |
| 管理指标缓存不随管理员令牌变化 | 键中加入**不可逆令牌指纹**，无令牌时不请求 | `lib/queryKeys.ts`、`components/admin/MetricsPanel.tsx` |

## 三、已优化功能（区分来源）

| 类别 | 内容 |
|---|---|
| **原有且保持** | 商品列表/搜索/分页、商品详情、活动列表与详情（时间窗优先的阶段判定）、秒杀提交与 5 种 `result` 反馈、`QUEUED→CREATED` 轮询、我的订单/订单详情、登录注册与 40100 统一处理、管理端令牌闸门/上传/预热/重置/指标/快照/死信重放 |
| **本次修复** | 上文第 1~5 节全部条目（均为既有功能的正确性修复，未新增业务页面） |
| **本次新增能力** | 全局错误边界、请求号透出、移动端导航、轮询超时提示与手动刷新、查询键工厂、用户域缓存清理、页码越界回退 |

未删除任何既有功能；未改后端契约；未升级依赖。

## 四、验证情况

### 自动化检查（修复后实测）

```
npx tsc --noEmit -p tsconfig.app.json   → exit 0（0 error）
npm run lint                            → exit 0（0 error，5 条既有 react-refresh 提示）
npx vitest run                          → 52 passed / 10 files（新增 15 条）
npm run build                           → ✓ built in 5.56s（按路由分包，dist 产物正常）
```

新增单测覆盖：轮询停止条件（6）、查询键隔离与缓存清理（4）、倒计时只触发一次（3）、错误边界兜底（2）。

### 真后端回归（经 `http://127.0.0.1:5173` 同源代理，`curl` + Cookie 罐）

| # | 场景 | 实测 |
|---|---|---|
| 1 | 首次进入首页 | `code=0`，`total=10 pages=3`，首条 `联调测试商品-机械键盘 / 39900分 / ON_SHELF` |
| 2 | 搜索商品 | `keyword=P4D4` → `total=1`，命中 `P4D4-ProdA` |
| 3 | 清除搜索 | 恢复 `total=10`（与首次一致） |
| 4 | 切换分页 | `page=2` → `data.page=2`、4 条、与第 1 页 ID 不同 |
| 5 | 商品不存在 | `code=40004`、`message=商品不存在`、`requestId` 有值 |
| 6 | 未登录参与秒杀 | `code=40100 请先登录` |
| 7 | 登录与会话恢复 | `code=0`、`user=p4d6probe`、`csrf 43` 字符；`me` 同样 `code=0` |
| 8 | 秒杀提交 | `result=QUEUED`、`orderNo=SK202610172207561140000` |
| 9 | 处理中状态跟踪 | 第 1 次轮询 `status=CREATED`（终态，前端停止） |
| 10 | 秒杀完成 | `我的订单` 命中本单、状态 `CREATED` |
| 11 | 重复提交 | `code=40901`、`result=DUPLICATED` |
| 12 | 秒杀业务失败 | 未开始活动 → `code=40001`、`result=REJECTED`、`message=活动未开始` |
| 13 | 退出登录 | `logout code=0`；随后 `me=40100` |
| 14 | 页面刷新 | `/`、`/products`、`/orders` 均 200；刷新后 `me=40100`（未登录态一致） |

### 未验证（不虚构）

| 项 | 原因 |
|---|---|
| 接口失败的用户可见反馈 | 后端全部正常，未人为断网/注入 500；该路径由 `ErrorState` + `http` 既有单测覆盖 |
| 前端防重复点击的视觉表现 | 无浏览器自动化；逻辑上按钮在 `mutation.isPending` 时禁用，后端幂等实测 `40901` |

## 五、遗留问题与后端限制

| 类型 | 内容 | 处理 |
|---|---|---|
| 后端限制 | 商品列表无排序/状态筛选项 | 前端不提供假筛选（不做本地排序冒充） |
| 后端限制 | 活动列表一次性返回、无分页参数 | 前端不分页 |
| 后端限制 | 无取消订单/支付/退款接口 | 不提供相关按钮 |
| 后端限制 | 活动 `status` 是创建时快照、不自动翻转 | 前端按时间窗判定（已有反向单测锁定） |
| 环境约束 | 若不设 `FLASH_STREAM_AUTO_POLL=true`，订单会停在 `QUEUING` | 已在启动文档中说明；前端会展示"排队中"并在 90s 后停止自动刷新、提示手动刷新 |
| 体验余项 | 列表无骨架到内容的过渡动画、无图片 `srcset` | 不影响业务正确性，未在本轮范围内 |

## 六、最终结论

**可以进入下一轮联调/验收。**

依据：
1. 核心业务链路（浏览 → 秒杀 → 排队 → 落单 → 订单查询）在真实后端上端到端通过，枚举与状态映射与后端代码一致；
2. 4 处高危缺陷已修复并有单测/实测佐证，其中"渲染期副作用"与"用户缓存串号"属于会随使用时间恶化的隐患；
3. 工程门禁全绿（tsc / eslint / 52 单测 / 构建），且改动集中在既有分层内，未破坏接口契约与既有页面；
4. 未验证项与后端限制已如实列出，不存在以假数据或假成功掩盖的缺陷。

---

## 七、第二轮：真实浏览器复检（新增修复 2 类 3 处）

首轮的验证手段是 `curl` + jsdom，覆盖不到浏览器运行期。第二轮补充了基于系统 Chrome 的真实页面核查（`frontend/scripts/browser-check.mjs`、`admin-check.mjs`），采集 `pageerror` / `console.error` / 失败请求 / 响应码，**抓到了两个只在浏览器里暴露的缺陷**。

### 7.1 新增缺陷与修复

| 编号 | 现象 | 根因 | 修复文件 | 验证 |
|---|---|---|---|---|
| D5 | 三个管理页整页「页面出现异常」，`pageerror: useAdminToken 必须在 <AdminTokenProvider> 内使用` | Provider 挂在页面内部的 `AdminShell`，而页面在顶部就调用 `useAdminToken()` | `router/index.tsx`（新增 `AdminLayout` 承载 Provider）、`components/admin/AdminShell.tsx`、`pages/admin/AdminDashboardPage.tsx` | `/admin` 显示令牌闸门，`pageErrors=0`；带令牌时指标面板 8 项渲染；路由级回归测试 3 条 |
| D6a | 管理控制台点「快速选择」→ `TypeError: activityId.trim is not a function` | 后端 13~15 位 `Long` 主键仍是 JSON number，写入了 string 状态 | `lib/id.ts`（新）、`api/{activity,product,order,rank}.ts`、`pages/admin/AdminDashboardPage.tsx` | 选活动 → 指标正常渲染，`pageErrors=0` |
| D6b | 商品详情**永远**显示「没有进行中的秒杀活动」 | 路由参数 string 与接口 number 用 `===` 比较恒 false | 同上 | `/products/90173199893504` 出现「有秒杀活动」+「去秒杀」 |

**根因归纳**：`http` 层只对 ≥16 位整数转字符串，13~15 位 ID 未覆盖，而 `Id` 被声明为 `string`。现统一在 API 边界归一化，连带修正了 queryKey 数字/字符串两套缓存的问题。

### 7.2 真实浏览器验证（Chrome headless）

首页 `共 10 件商品` → 搜索 `共 1 件` → 清空 `共 10 件`；活动详情「未开始」按钮禁用（时间窗判定）；商品详情关联活动正常；未登录 `/orders` 跳登录；登录后回跳 `/orders` 且导航显示用户；订单 4 条、详情 0 错误；`/admin` 闸门与指标正常；移动端抽屉 5 个入口；无效路由兜底；**接口失败（拦截 `/api/products`）出现错误态 + 重新加载按钮，未白屏**；全程 `pageErrors=0`。

### 7.3 门禁（第二轮修复后）

```
vitest  59 passed | 5 skipped（真后端 live-smoke，需 VITE_API_BASE_URL 才运行）
tsc     0 error
eslint  0 error（6 warnings：Provider 与 hook 同文件，既有）
build   ✓ 5.72s
```

### 7.4 遗留（后端数据，非前端可修）

种子商品图片 4 条 404：`/uploads/202609/*` 在磁盘上不存在（`uploads/` 只有 3 个 `202610` 文件），数据库种子 `image_url` 指向了未落盘的文件。前端已按设计降级为「暂无图片」占位，不裂版、不抛错；修复需后端补文件或修正种子数据。

### 7.5 复现方式（可选）

`scripts/*.mjs` 为本地核查脚本，依赖 `playwright-core`（**未写入 package.json**，避免改变工程依赖）：
`npm i --no-save playwright-core`，然后 `node scripts/browser-check.mjs`（可带 `CHECK_ACCOUNT` / `CHECK_PASSWORD` 环境变量）。
