# 闪购集市与运营管理中心

基于 Vue 3、TypeScript、Vite 与 Element Plus。一个前端包含顾客商城与运营后台，页面调用项目后端接口；不生成模拟商品、订单或用户记录。

## 页面

- `/`：已上架商品、秒杀活动、价格与库存展示；活动库存每 20 秒刷新。
- `/products/{商品编号}`：商品详情、秒杀资格检查、下单与订单状态跟踪。
- `/sign-in`、`/register`：账号密码登录与注册；登录态使用后端的 `HttpOnly` Cookie。
- `/account/orders`：当前账号的秒杀订单与处理状态。
- `/dashboard`、`/products`、`/activities`、`/operations`、`/users`：运营后台。

## 首次准备

需要 Node.js、Java 17、MySQL 与 Redis。先启动后端所需服务并执行数据库初始化；已有数据库还需手动执行：

```text
E-commerceFlashSaleSystem/src/main/resources/db/migration/V20261010__user_accounts_and_sessions.sql
```

前端开发代理把 `/api` 转发到 `http://localhost:8081`。

```powershell
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。后端与数据库说明见仓库根目录的 `CLAUDE.md`、`docs/database.md`、`docs/interface.md`。

## 构建与检查

```powershell
npm run typecheck
npm run build
npm run preview
```

## 配置说明

后端浏览器会话的 `SESSION_COOKIE_SECURE` 在本地开发默认为 `false`；生产环境应使用 HTTPS 并设置为 `true`。`ALLOWED_ORIGINS` 需填允许访问后端的前端来源。登录后防跨站令牌仅保存在内存，写请求会发送 `X-CSRF-Token`。

后台继续使用现有 `X-Admin-Token` 管理令牌。前端 `VITE_ADMIN_TOKEN` 会进入浏览器构建产物，只适用于本地演示，不可当作生产秘密凭证。旧压测令牌兼容默认关闭。

商品原价和秒杀价在后台以元输入，发送后端时换算为分。商品图片由现有图片上传接口保存。
