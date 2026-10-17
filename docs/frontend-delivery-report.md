# 前端交付报告 —— 秒杀商城（用户端 + 管理端）

交付时间：2026-10-17 ｜ 目录：`frontend/` ｜ 依赖后端：`E-commerceFlashSaleSystem`（多运行时 + 网关 8090）

## 一、工程分析

| 项 | 决策 | 理由 |
|---|---|---|
| 构建 | Vite 5 + React 18 + TypeScript 5（strict） | 需求（build.md）指定；路由级懒加载天然分包 |
| 样式 | Tailwind 3.4.17 + 自定义 token（品牌红 `#E1251B`） | 与 `baseline-ui` 约束一致：用默认尺度、单一强调色、不写渐变/紫色 |
| 组件 | Radix Dialog（结果弹窗）+ 自研 Button/Input/Card/Badge/Pagination 等 | 交互可达性交给原语，视觉与业务样式自控 |
| 服务端状态 | `@tanstack/react-query` | 缓存分级、`refetchInterval` 回调实现"终态即停"的轮询 |
| HTTP | 原生 `fetch` 封装（不引 axios） | 需要精细控制 `credentials`、CSRF 重放、大整数安全解析 |
| 路由 | React Router 6 + `RequireAuth` 守卫 | 未登录跳登录并保留回跳地址 |

**关键设计**：`types → http → api → hooks/queries → pages` 五层单向依赖，页面里不出现裸 URL；认证/CSRF/错误码全部收敛在 `lib/http.ts`。

## 二、已完成功能

### 用户端
| 页面 | 能力 |
|---|---|
| 首页 / 商品列表 `/`、`/products` | 关键词搜索（300ms 防抖）、分页（`pageSize=12`，后端上限 100）、进行中活动促销条（无则不渲染）、骨架屏 / 空态 / 错误重试、图片失败占位 |
| 商品详情 `/products/:productId` | 真实价格（分→元）、库存、状态徽章、关联秒杀活动与"去秒杀"入口 |
| 活动列表 `/activities` | 按前端推导阶段筛选（全部/进行中/即将开始/已售罄/已结束）、倒计时、已抢进度条 |
| 活动详情 / 秒杀 `/activities/:activityId` | 秒杀价与原价、倒计时（到点自动刷新数据）、库存进度、限购、抢购榜 TOP10、秒杀按钮的 6 种阶段态与禁用原因 |
| 秒杀提交 | 真实 `POST /api/seckill/execute`；按 `result` 处理 5 种结果；`QUEUED` 后用订单号轮询至终态（1.5s、页面隐藏暂停、离页自动停、不叠加定时器） |
| 登录 / 注册 `/login`、`/register` | 表单校验（密码 ≥10 位对齐后端）、错误码文案、成功回跳来源页 |
| 个人中心 `/profile` | 账户信息（来自 `/api/auth/me`）、订单入口、退出登录 |
| 我的订单 `/orders`、`/orders/:orderNo` | 状态筛选、状态徽章、空态/错误态、**有排队订单时 2s 自动刷新、全部终态自动停止**、订单详情轮询 |

### 管理端
| 页面 | 能力 |
|---|---|
| `/admin` | 令牌闸门（仅 `sessionStorage`）、活动选择（公开列表快捷选择）、指标面板（5s 自动刷新）、快照落库、死信批量重放 |
| `/admin/products` | 商品录入（元→分换算）、图片上传（multipart、5MB 预检、失败兜底）、状态选择、返回新商品 ID |
| `/admin/activities` | 活动创建（时间按后端 `yyyy-MM-dd HH:mm:ss` 提交）、预热、重置库存 |

### 工程质量
- 统一 `ApiError`（code/message/data/httpStatus）+ 错误码中文映射；网络异常与超时可重试
- **大整数保护**：`Long` 型 ID ≥16 位时自动转字符串，避免 `1788829276582000000` 精度失真（有单测）
- `40100` 全局登出；`40301` 自动刷新 CSRF 后重放一次
- 路由级代码分割、`text-balance` 标题、`tabular-nums` 数字、`h-dvh`、焦点环与禁用态、`aria-*` 标注

## 三、质量门禁（全部通过）

| 项 | 命令 | 结果 |
|---|---|---|
| 类型检查 | `npx tsc --noEmit -p tsconfig.app.json` | **exit 0**，0 error |
| Lint | `npm run lint` | **0 error**（5 条 `react-refresh` 提示：Provider/hook 与组件同文件，非缺陷） |
| 单测 | `npm test` | **32 passed / 5 files** |
| 生产构建 | `npm run build` | 成功，`dist/` 36 个产物（按路由分包） |
| 冒烟 | `vite dev` + 真实后端 | 见第四节 |

**单测覆盖**：金额/时间/倒计时格式化、活动阶段状态机（含"后端 status 是快照"的反向用例）、HTTP 信封与错误码、大整数安全解析、CSRF 40301 重放、40100 回调和订单终态判定，以及列表页三态渲染。

## 四、真实联调记录（经 Vite 同源代理 → 网关 8090，2026-10-17）

| # | 场景 | 请求 | 实测结果 |
|---|---|---|---|
| 1 | 页面可达 | `GET /`、`/products/:id`、`/admin` | 均 **200**（SPA 路由回退正常） |
| 2 | 商品列表 | `GET /api/products?page=1&pageSize=3` | `code=0`，`total=9`，`pages=3` |
| 3 | 活动列表 | `GET /api/seckill/activities` | `code=0`，24 条 |
| 4 | 未登录访问受保护接口 | `GET /api/auth/me`（无 Cookie） | `code=40100` |
| 5 | 登录 + 会话恢复 | `POST /api/auth/login` → `GET /api/auth/me` | `code=0`，`csrfToken` 43 字符；`me` 返回 `p4d6probe` |
| 6 | 管理端建活动 + 预热 | `POST /api/admin/seckill/activities` → `/preheat` | `code=0`，新活动 `90292088459264`，库存 3/3 |
| 7 | **秒杀提交** | `POST /api/seckill/execute`（带 `X-CSRF-Token`） | `code=0`，`result=QUEUED`，`orderNo=SK202610172117481830000` |
| 8 | **订单轮询** | `GET /api/seckill/orders/{orderNo}` | 第 1 次即 `status=CREATED`（终态，前端停止轮询） |
| 9 | 我的订单 | `GET /api/seckill/users/me/orders` | `code=0`，2 条，首条 `CREATED` |
| 10 | 抢购榜 | `GET /api/rank/top10?activityId=…` | `code=0`，1 条 |
| 11 | 管理指标 | `GET /api/admin/seckill/activities/{id}/metrics` | `orderCount=1`、`redisStock=2`、`successCount=1` |
| 12 | 重复秒杀 | `POST /api/seckill/execute`（同用户同活动） | `code=40901`、`result=DUPLICATED`（幂等生效） |

## 五、联调中修正的契约偏差（重要）

| # | 偏差 | 处理 |
|---|---|---|
| 1 | 商品状态真实值 `ON_SHELF`（我按 `ON_SALE` 写） | 全局修正 |
| 2 | 活动状态真实值 `RUNNING`（我按 `IN_PROGRESS` 写） | 全局修正 |
| 3 | 秒杀入队结果真实值 **`QUEUED`** | 修正枚举与分支 |
| 4 | 订单成功态真实值 **`CREATED`**（无支付环节） | 修正终态集合，否则会无限轮询 |
| 5 | 活动时间必须 `yyyy-MM-dd HH:mm:ss` | 管理端提交前转换 |
| 6 | `activity.status` 是创建时快照、不随时间翻转 | 阶段判定改为**时间窗优先**，并补反向单测 |

> 第 3/4 条如果不在联调中暴露，会分别导致"成功后当成失败提示"和"永远轮询"两个线上级缺陷。

## 六、已知问题与限制

| 类型 | 内容 | 影响 / 规避 |
|---|---|---|
| 环境依赖 | 必须设 `FLASH_STREAM_AUTO_POLL=true` 才有消费者建单 | 否则订单停在 `QUEUING`；已写入映射文档第 8 节 |
| 环境依赖 | `/api/rank/**` 与 `/api/admin/**/metrics` 落在单体（8081），单体未启动时网关返回 503 | 前端展示"榜单暂时不可用"，不崩页面 |
| 设计取舍 | 商品列表不支持按状态筛选（后端接口无此参数） | 不做假筛选；改由活动时间窗表达 |
| 设计取舍 | 秒杀资格 `check` 接口返回体未定型 | 不臆造字段，靠 `execute` 的 `result` + 订单状态表达 |
| 体验 | 管理端未做活动列表管理/编辑删除（后端仅提供创建、预热、重置、指标） | 按后端能力交付，不造接口 |
| 状态 | 上一轮创建的活动 `status` 为 `NOT_STARTED`（后端不翻转） | 前端已按时间窗正确显示为"进行中"并可秒杀（联调第 7 步即在该活动上成功） |

## 七、启动方式

```powershell
# 0) 前置：JDK21、MySQL 8、Redis 8（db=1）、Nacos；后端仓库根 .env 提供口令与 ADMIN_TOKEN

# 1) 启动后端（order 必须带 FLASH_STREAM_AUTO_POLL=true）
cd D:\Project\E-commerceFlashSaleSystem\E-commerceFlashSaleSystem
$env:JAVA_HOME='D:\JDK\jdk-21.0.12\jdk-21.0.12.1'; $env:FLASH_STREAM_AUTO_POLL='true'
.\mvnw.cmd -pl flash-sale-auth,flash-sale-product,flash-sale-activity,flash-sale-seckill,flash-sale-order,flash-sale-gateway -am spring-boot:run
# 需要榜单/指标/OpenAPI 时另外启动单体
.\mvnw.cmd -pl flash-sale-bootstrap spring-boot:run

# 2) 启动前端
cd D:\Project\E-commerceFlashSaleSystem\frontend
npm install
npm run dev          # http://127.0.0.1:5173（已代理 /api 与 /uploads → 8090）

# 3) 其他命令
npm test             # 单测
npm run lint         # ESLint
npm run build        # 生产构建
npm run preview      # 预览构建产物（4173，同样带代理）
```

环境变量（`frontend/.env`，可选）：`VITE_PROXY_TARGET`（默认 `http://127.0.0.1:8090`）、`VITE_API_BASE_URL`（默认空=同源）。

管理端：访问 `/admin`，粘贴后端 `.env` 的 `ADMIN_TOKEN`；令牌仅存当前标签页 `sessionStorage`。

## 八、交付物清单

```
frontend/
├── package.json / vite.config.ts / tailwind.config.js / postcss.config.js / eslint.config.js
├── tsconfig*.json / index.html / .env.example / .gitignore
└── src/
    ├── main.tsx App.tsx router/index.tsx
    ├── api/{auth,product,activity,seckill,order,rank,admin}.ts
    ├── types/{api,auth,product,activity,order,admin}.ts
    ├── lib/{http,errors,format,activityPhase,cn,queryClient}.ts
    ├── stores/{auth.tsx,adminToken.tsx}
    ├── hooks/{useDebouncedValue,useDocumentVisible}.ts + queries/*
    ├── components/{layout,ui,product,activity,seckill,order,admin,auth}/*
    ├── pages/{ProductList,ProductDetail,ActivityList,ActivityDetail,Login,Register,Profile,Orders,OrderDetail,NotFound}Page.tsx
    ├── pages/admin/{AdminDashboard,AdminProduct,AdminActivity}Page.tsx
    └── __tests__：lib/*、hooks/*、pages/*（5 个文件 32 条）
docs/frontend-backend-mapping.md    # 接口映射与契约偏差
docs/frontend-delivery-report.md    # 本文件
```
