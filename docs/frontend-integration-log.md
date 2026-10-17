# 前后端联调记录（2026-10-17 21:40 ~ 21:52）

> 方式：**所有请求走前端同源代理 `http://127.0.0.1:5173` → 网关 `8090`**（后端会话 Cookie 为 `SameSite=Strict`，直连 8090 跨端口不携带 Cookie，测出的 40100 是假阴性）。
> 结论：**全部用例通过**；过程中发现并修复 2 处真实缺陷（网关路由缺口、前端图片绝对地址），另 1 项为后端刻意的业务守卫（非缺陷）。

## 一、环境与就绪状态

| 项 | 值 |
|---|---|
| JDK | `D:\JDK\jdk-21.0.12\jdk-21.0.12.1` |
| 基础设施 | MySQL 3306 ✅ / Redis 6379（db=1）✅ / Nacos 8848 ✅ |
| 后端进程 | auth `8082`、product `8084`、activity `8085`、seckill `8086`、order `8087`、gateway `8090`、单体 `8081` —— **7/7 监听成功**，日志 `APPLICATION FAILED TO START` = **0** |
| 关键开关 | 全局注入 `FLASH_STREAM_AUTO_POLL=true`（不注入则订单永远 `QUEUING`） |
| 前端 | `vite dev` `5173`，`/api` 与 `/uploads` 代理到 8090 |
| 网关首探 | `GET :8090/api/products` → HTTP 200 |

启动命令（本次实际执行的形态）：

```powershell
$env:JAVA_HOME='D:\JDK\jdk-21.0.12\jdk-21.0.12.1'; $env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:FLASH_STREAM_AUTO_POLL='true'
cd D:\Project\E-commerceFlashSaleSystem\E-commerceFlashSaleSystem
foreach ($m in 'flash-sale-auth','flash-sale-product','flash-sale-activity','flash-sale-seckill',
               'flash-sale-order','flash-sale-gateway','flash-sale-bootstrap') {
  Start-Process -FilePath ".\mvnw.cmd" -ArgumentList "-pl",$m,"spring-boot:run" -WindowStyle Hidden `
    -RedirectStandardOutput "$env:TEMP\fe-$m.log" -RedirectStandardError "$env:TEMP\fe-$m.err"
}
# 等 8081/8082/8084/8085/8086/8087/8090 全 True，再等 12s 让 Nacos 注册生效
cd ..\frontend; npm run dev   # 5173
```

## 二、执行前的网关路由预检（code-explorer）

逐条核对 22 条前端请求路径，发现 **1 条未登记**：

| 路径 | 预检结论 |
|---|---|
| `/api/admin/seckill/dead-letters/replay-batch` | **未命中任何专属路由**，会落到 `/api/**` 兜底到单体；而单体 `scanBasePackages` 不含 `order` 包，处理器只在 `AdminMessageController` ⇒ **必然 404** |
| 其余 21 条 | 均已登记（`/api/rank/top10`、`.../metrics` 靠 `/api/**` 兜底到单体；`.../snapshot` 由 order 的精确路由承载） |

**修复**：`flash-sale-gateway/src/main/resources/application.yaml` 的 order 路由 Path 列表补 `/api/admin/seckill/dead-letters/replay-batch`（与单条 `/replay` 并列，附注释说明原因）。

## 三、用例矩阵与实测结果

### A 组：页面可达（10/10 通过）

| 路径 | HTTP |
|---|---|
| `/`、`/products`、`/activities`、`/activities/{id}`、`/login`、`/register`、`/orders`、`/profile`、`/admin`、`/admin/products` | 全部 **200**（SPA 路由回退有效） |

代理与转译：`/api/products` 200、`/api/seckill/activities` 200、`/uploads/不存在.png` 404（说明代理已到达 product 服务）、`/src/main.tsx` 200、`/src/pages/ActivityDetailPage.tsx` 200。

### B 组：公开接口（6/6 通过）

| 用例 | 实测 |
|---|---|
| 商品列表 | `code=0`，`total=9`、`pages=3`、`pageSize=3`；首条 `P4D4-ProdA`、`ON_SHELF`、`20/20`、`2049` 分 |
| 关键词搜索 `keyword=P4D4` | `code=0`、`total=1`、命中 `P4D4-ProdA` |
| 商品详情 | `code=0`、`ON_SHELF` |
| 活动列表 | `code=0`、25 条 |
| 活动详情 | `code=0`、`stock=2/3`、`limitPerUser=1`、时间 `2026-10-17 21:12:47 ~ 23:17:47` |
| 榜单（单体承载） | `code=0`、1 条 |

### C 组：认证与 CSRF（7/7 通过）

| 用例 | 实测 |
|---|---|
| 未登录 `GET /api/auth/me` | **40100** |
| 未登录 `GET /api/seckill/users/me/orders` | **40100** |
| 登录 | `code=0`、`user.id=90193083998208`、`csrfToken` 43 字符 |
| 会话恢复 `me` | `code=0`、`p4d6probe`、`status=1` |
| `GET /api/auth/csrf` | `code=0`、43 字符 |
| 错误口令 | **40101**（防暴破计数生效，未见异常） |
| 登出 → 再 `me` | 登出 `code=0` → 后续 `me` **40100** ✅ |
| 旧 CSRF 登出（反向） | **40301** → 取新 token 重放 `code=0`（等价于前端自动重试逻辑） |

### D 组：秒杀主链路（10/10 通过）

| 用例 | 实测 |
|---|---|
| 建活动（空格格式时间） | `code=0`、`activityId=90293827293184`、`status=NOT_STARTED`（**创建时快照**） |
| **反向**：ISO 时间（带 `T`） | **40001**（证明前端 `toServerTime` 必要） |
| 预热库存 | `code=0`、`stock=3` |
| 活动详情 | `code=0`、`stock=3/3`、`seckillPrice=2999`、`limit=1` |
| **秒杀提交** | `code=0`、**`result=QUEUED`**、`orderNo=SK202610172146067790000` |
| **订单轮询**（2s） | 第 1 次 `status=CREATED`、`seckillPrice=2999` ⇒ 前端停止轮询 |
| 我的订单 | `code=0`、3 条、命中本单 `CREATED` |
| 榜单 | `code=0`、1 条、`rank=1` |
| 重复提交 | **`code=40901` + `result=DUPLICATED`**（幂等） |
| 指标交叉核对 | `orderCount=1`、`redisStock=2`（=3−1）、`successCount=1`、`duplicateReject=1` ✅ |

### E 组：管理端（9/9 通过）

| 用例 | 实测 |
|---|---|
| 无 `X-Admin-Token` 建活动 | **`code=40100`**（拒绝） |
| 图片上传（真实 1×1 PNG，`file` 字段） | `code=0`、`storageType=local`、`url=http://172.21.112.1:8084/uploads/202610/d07f….png` |
| 商品录入 | `code=0`、`productId=90293855236096`（`originalPrice=39900` 分） |
| 商品详情回读 | `code=0`、名称/价格/库存/状态/图片一致 |
| 重置库存（进行中活动） | **`code=40001`「活动进行中，禁止重置库存」** ⇒ 后端刻意守卫，非缺陷 |
| 指标 | `code=0`、`redisStock=2`、`orderCount=1` |
| 指标快照（网关→order） | `code=0`、HTTP 200 |
| 死信批量重放（**修复后**） | `code=0`、`attempted=2`、`succeeded=0`、`failed=2`，`failures[0]=not-exist-1：死信消息不存在` ⇒ **路由修复生效**（修复前必 404） |
| 死信批量空列表 | `code=0`、`attempted=0`、`failed=0` |

### R 组：修复后复测（5/5 通过）

| 用例 | 实测 |
|---|---|
| 图片同源路径经代理 | `/uploads/202610/d07f….png` → **200** |
| dev server 转译新模块 | `/src/lib/asset.ts` → 200（HMR 生效） |
| 进行中活动重置 | `40001 活动进行中，禁止重置库存` ✅ 守卫 |
| 未开始活动：建→预热→重置 | `code=0`、`stock=2`、指标 `redisStock=2` ✅ |
| 未开始活动秒杀 | `code=40001`、`result=REJECTED`、`message=活动未开始` ✅ |

## 四、发现项（3 条真实 + 2 条测试注意点）

| # | 发现 | 定性 | 处理 |
|---|---|---|---|
| 1 | 网关未登记 `/replay-batch`，批量死信重放必 404 | **真实缺陷（配置缺口）** | 已在 `application.yaml` 补 Path；E8 实测 `code=0` |
| 2 | 上传接口返回**内网绝对地址** `http://172.21.112.1:8084/uploads/...`，前端直连会在非本机环境全部裂图，并绕开同源代理 | **真实缺陷（前端健壮性）** | 新增 `lib/asset.ts:toSameOriginPath`：回环/私网 IP 或 8084 端口 → 取 `/uploads/...` 同源路径；OSS/CDN 外链保持原样。`ProductImage` 与上传预览统一走它；补 5 条单测 + 列表页渲染断言 |
| 3 | 进行中活动重置库存返回 40001 | **非缺陷**（防超卖守卫） | 记录行为：重置只允许非进行中活动；前端提示后端 message 即可 |
| 4 | `Invoke-RestMethod` 的 POST 经 Vite 代理会挂起/500，而 curl 与浏览器正常 | 测试客户端伪故障（.NET 默认发 `Expect: 100-continue`，Node 代理处理不当） | 联调统一改用 `curl.exe` + Cookie 罐；真实浏览器不受影响 |
| 5 | `GET /api/auth/csrf` 会**轮换** token，旧 token 立即失效（40301） | 预期行为 | 前端 `http.ts` 已实现 40301 自动取新 token 重放一次；C 组反向用例已验证该语义 |

## 五、门禁（修复后）

```
npm test            37 passed / 6 files（新增 asset 归一化 5 条 + 列表页图片断言）
npx tsc --noEmit    0 error
npm run lint        0 error（5 条 react-refresh 提示）
npm run build       ✓ built in 5.68s
```

## 六、当前运行状态与停止方式

**服务保持运行**，可直接人工验收：

| 入口 | 地址 |
|---|---|
| 前端（推荐从这里点） | http://127.0.0.1:5173 |
| 网关 | http://127.0.0.1:8090 |
| 单体（榜单/指标/OpenAPI） | http://127.0.0.1:8081/swagger-ui/index.html |

账号：`p4d6probe` / `P4d6Probe2026!`；管理端 `/admin` 令牌取后端仓库 `.env` 的 `ADMIN_TOKEN`。
可验收数据：活动 `90293827293184`（1 单已成交、库存 2）、`90293952248832`（未开始、库存 2）、商品 `90293855236096`（联调测试商品）。

一键停止：

```powershell
foreach ($p in 5173,8081,8082,8083,8084,8085,8086,8087,8090) {
  Get-NetTCPConnection -LocalPort $p -State Listen -EA SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
}
```

日志位置：`%TEMP%\fe-flash-sale-<module>.log`、`%TEMP%\fe-vite.log`。
