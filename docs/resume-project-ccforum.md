# 秋招简历项目条目：校园圈子社区 CC-Forum（精简版）

> 定位：后端开发工程师（AI 应用方向）· 简历第二项目（全栈/后端广度型），篇幅约第一项目的 1/2。
> ⚠️ 计数类数字来自你的原稿，粘贴前按实际核对；性能类表述均为可由设计推导的事实，无估算指标。

---

## 一、精简条目（推荐粘贴）

### 校园圈子社区 CC-Forum —— 图文社交社区（个人全栈项目）

**技术栈：** Spring Boot 3 + Spring Security 6 + JWT · MySQL 8 + MyBatis-Plus · Redis 7 · WebSocket/STOMP · uni-app(Vue 3) · React 18 + TypeScript + Ant Design 5

面向高校师生的图文圈子社区，覆盖微信小程序 / H5 / 管理后台三端，独立完成从库表设计、后端 API 到三端编码全流程。

- **认证与主动失效**：Spring Security 6 过滤器链 + JWT 声明式角色鉴权，BCrypt 加密；登出 Token 写 Redis 黑名单（TTL = 剩余有效期）、用户封禁写 Redis 标记实时拦截，解决纯 JWT 无法主动失效问题。
- **计数写放大治理**：点赞/收藏等高频计数先 `INCR` Redis 缓冲，每 30s 定时任务以 Lua 原子 GET+DEL 取增量、批量 SQL（`GREATEST(field+delta,0)`）落库，热点计数写频率由"每请求 1 次"降为"每周期 1 次"，缓解行锁竞争与 DB 压力；SCAN 替代 KEYS；低频评论直写。
- **事件驱动解耦**：点赞/评论/关注发布领域事件，`@Async` 线程池异步消费通知，TaskDecorator 透传 SecurityContext，主接口响应不被通知链路拖慢。
- **实时通知**：WebSocket + STOMP 双端点——SockJS 兼容 H5、原生 WS 适配微信小程序（不兼容 SockJS），用户级定向推送。
- **工程化**：MyBatis-Plus 逻辑删除/自动填充/JSON TypeHandler；统一 `Result<T>` + 全局异常；OSS/本地存储接口隔离；uni-app 分包懒加载 + 乐观更新；React 管理后台含数据看板。

**量化：** 三端统一后端 API；11 个后端模块、90+ Java 源文件、10 张核心表；移动端 17 页（主包 5 + 分包 12）、管理后台 6 页；高频计数写路径经 30s 窗口聚合落库。

**个人贡献：** 独立全栈开发；主导计数缓冲双轨策略、认证主动失效、事件驱动通知等关键设计。

---

## 二、极简版（简历最紧张时）

> 图文圈子社区全栈项目（小程序/H5/管理后台三端）。后端：Spring Security 6 + JWT 鉴权，登出 Token 入 Redis 黑名单实现主动失效；点赞等高频计数 INCR 缓冲 + 30s 批量落库治理写放大；事件驱动 + @Async 异步通知，TaskDecorator 透传安全上下文；WebSocket 双端点兼容小程序/H5 实时提醒。11 模块、10 表、90+ Java 文件。

---

## 三、上简历前自查

1. 数字口径（10 表 / 11 模块 / 90+ / 17 页 / 6 页）与当前代码一致；
2. 面试口径：为每条结论备好代码位置（黑名单 Filter、Lua 脚本、定时任务、TaskDecorator、双端点配置）。

---

## 四、数字自查命令清单（在 CC-Forum 仓库执行；本仓库无该项目代码，无法代为核对）

```bash
# 后端模块数（多模块看 pom 数；单模块看顶层业务包数）
find . -name pom.xml | wc -l
# Java 源文件数（只算 main）
find . -path "*/src/main/java/*" -name "*.java" | wc -l
# 核心表数（按建表脚本统计）
grep -rc "CREATE TABLE" --include=*.sql . | awk -F: '{s+=$2} END{print s}'
# uni-app 页面数（主包 + 分包）
grep -c '"path"' pages.json
# React 管理后台页面数
find src/pages -name "*.tsx" 2>/dev/null | wc -l
```

> 口径建议：粘贴前逐项复核。若某项对不上，宁可用模糊量级（"十余个模块""90+ 源文件"）也不要在面试被追问具体数时答不出来；性能类表述保持"可由设计推导"（如"计数写由每请求 1 次降为每周期 1 次"），不要写未实测的 QPS/延迟数字。
