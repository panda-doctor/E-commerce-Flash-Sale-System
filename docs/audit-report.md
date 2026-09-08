# 全量代码审计报告（2026-09-08）

> 范围：E-commerceFlashSaleSystem（后端 Spring Boot 3.3 + 前端 Vue3 + docs 契约）全量业务代码。
> 方式：后端核心链路 / 接口与支撑层 / 前端三路逐文件精读 + 关键证据人工复核；本次仅出清单，未改动代码。
> 级别：高（丢单/超卖/失真/泄露/契约破坏）＞中（边界/异常/一致性）＞低（规范/日志/死代码）。

---

## 一、高危

### R1 · `IdGenerator` 超长 ID 破坏前后端全链路
- 位置：`common/util/IdGenerator.java:28`（`timestamp*1_000_000+sequence` ≈1.78e18）
- 后果：远超 JS 安全整数 2^53；活动/商品/用户/订单 Long 数字 ID 经 JSON 反序列化即失真。管理台新建活动 → 详情/榜单/指标/订单回显全部 404（`ActivityDetailView:20 Number(route.params.id)`、`store.js:14 Number(localStorage)` 回环放大）。
- 建议：VO/DTO 的 ID 字段 `@JsonSerialize(using=ToStringSerializer.class)` 输出字符串，前端全程按字符串透传（去 `Number()` 回环）；或改雪花/自增。

### R2 · `execute` 发消息失败不回滚库存与令牌
- 位置：`service/impl/SeckillServiceImpl.java:203-208`
- 后果：Lua 已扣库存、令牌已建（30min），`sendMessage` 失败仅抛 500 → 该用户 30 分钟内重试全部"重复秒杀"，且库存永久悬空（无订单无补偿）。
- 建议：catch 中反向补偿（Lua `INCR` 库存 + `DEL` 令牌）后重抛；或引入入队重试/补偿任务。

### R3 · 无鉴权 + 密钥明文
- 位置：全部 `/api/admin/**`、`/api/seckill/execute`；`application.yaml`、`config/AiLlmProperties.java:31`
- 后果：任意调用者可替任意 userId 下单/改活动/无限上传；AI Key、DB 口令明文入库。
- 建议：统一 HandlerInterceptor（userId 取登录态、admin 令牌）；敏感接口限频；密钥走 `AI_LLM_API_KEY`/`OSS_*` 环境变量并轮换。

### R4 · 活动"更新"不清预热缓存
- 位置：`service/impl/SeckillActivityServiceImpl.java:113-131`
- 后果：预热后改价/改库存，详情与 execute 仍走旧缓存（旧价旧库存落单）；商品更新同缺陷链。
- 建议：update 成功删除 `seckill:activity:{id}`、`seckill:stock:{id}` 并置 `preheat_status=0`；`version` 补 `@Version` 乐观锁。

### R5 · 毒消息永留 PEL + 死信无回放/无补偿
- 位置：`stream/consumer/SeckillOrderConsumer.java:196-199`
- 后果：解析/基础设施异常只 log 不 ACK，坏消息每轮重试无限循环、PEL 增长；达到 3 次进死信后无人回放；订单丢失时令牌/库存不回收。
- 建议：parse 失败也落日志并走 `handleFailure` 计数，超阈值转死信并告警；死信提供人工回放；进死信触发库存/令牌补偿。

---

## 二、接口契约与参数一致性（中高）

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| C1 | `controller/seckill/OrderController.java:34` | 状态仅 `==1?CREATED:QUEUING`，FAILED(2)/CANCELLED(3) 折叠为 QUEUING | 按 OrderStatusEnum 输出 name，区分 QUEUING/CREATED/FAILED/CANCELLED |
| C2 | `OrderController.java` 返回体 | `createTime/orderId` 与 interface 4.9 `createdAt` 命名不符；文档 `PENDING` vs 实现 `QUEUING` | 统一命名与枚举文案 |
| C3 | interface 4.10 | `GET /api/seckill/users/{userId}/orders` 未实现 | 补按 userId 查询接口 |
| C4 | `common/exception/GlobalExceptionHandler.java` | 缺 MissingServletRequestParameter / MissingPathVariable / MethodArgumentTypeMismatch / MaxUploadSizeExceeded → 参数缺失与类型错误全落 50000；404 非统一 Result | 逐类新增 handler（40001 / 413），404 全局化 |
| C5 | `SeckillActivityServiceImpl.java:218-229` | check 未预热降级 DB 库存放行，execute 未预热报库存不足，口径不一致 | 未预热统一返回"未开放/请先预热" |
| C6 | execute 失败响应 | 40901/40902/42900 的 `data=null`，与 interface 4.8 `data:{result}` 不符 | 失败路径包装 SeckillResponse |

---

## 三、异常/边界/数据一致（中）

- **E1** `SeckillCacheService.resetStock` 无调用方（死代码，接入需防活动进行中复活库存）；`RedisConfig.decrStockScript` bean 无引用。建议删除或按预热语义接入。
- **E2** `limitPerUser` 仅透传，DB 以 `uk_activity_user` 硬性一人一单，配置 >1 不生效。建议移除字段或实现额度语义。
- **E3** 商品/活动创建缺业务校验：`endTime>startTime`、价格非负、库存 >0、productId 存在性（否则 FK 异常落 50000）。
- **E4** 限流仅按 userId（60s/5 次）：多活动连点易误伤、换 userId 可绕过。建议按 userId+activity 或叠加 IP 维度、参数可调。
- **E5** 文案问题：`SeckillCacheService.java:75` 返"重复…傻鸟！！！"；OSS 把 `e.getMessage()` 原样回用户（含 SDK 信息）。统一可读文案、细节进日志。
- **E6** 上传仅校验扩展名/大小，Content-Type 全信客户端。建议魔数探测 + 按扩展名固定 Content-Type。

---

## 四、前端完整闭环

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| F1 | `ActivityDetailView.vue:59-61,101-103` | won/duplicated 后按钮仍可点，重复打 execute/查榜弹窗 | canBuy/btnDisabled 追加 won/duplicated/lost 置灰 |
| F2 | `AiServiceView.vue:56-58` | 侧栏按 status 恒判"即将开抢"（预热不翻转 status） | 用 startTime/endTime+now 本地判定 |
| F3 | `ActivityListView.vue:110-119` | 列表 catch 静默置空，误示"暂无场次" | catch 内 toastErr |
| F4 | `ActivityDetailView.vue:223-227` | 轮询 catch 分支无提示 | 补 toastErr 文案 |
| F5 | `AdminView.vue:125-138` | 上传无前端 MIME/size 预检 | onFileChange 预检 jpeg/png/webp/gif ≤5MB |
| F6 | `AiServiceView.vue:104-129` | sendText 无 typing 防重入 | 入口 if(typing) return + 按钮禁用 |
| F7 | `AdminView.vue` | 改 productId 后未重载直接保存 → 脏写；"演示用户 #1001"与 uid 不同步 | 保存前重载/比对；实时取 userStore |
| F8 | 轮询组件 | 后台 tab 下 RankBoard/MetricsPanel/App health 仍打点 | document.hidden 统一暂停 |
| F9 | 低 | 未用 import/STATUS_TEXT 死代码；汉堡按钮无 handler；AI 页语音/附件/卡片死 UI | 清理或实现 |

---

## 五、代码规范 / 教学噪声（可后置或豁免）

`SeckillServiceImpl` 顶部 30+ 行 v1 复盘注释、`SeckillActivityController` 类尾大段注解、`ProductServiceImpl:209-241`、Mapper XML 尾注释、`CacheKeyConstant` 教学注释 —— 建议迁入 docs。
文档漂移：`ImageStorageService` 注释仍写 `storage.type`（已改 `aliyun.oss.enabled`）；`AiLlmProperties` 默认 model 与 yaml 不一致。
日志：`application.yaml` 包日志 WARN 会吞秒杀关键 `log.info`（命中缓存/扣库存/消费/入榜）留痕，建议 `com.ghb` 保留 INFO。

---

## 总体结论

可靠底座：Lua 原子扣减（无超卖）、幂等令牌、Stream XACK + 双唯一键幂等、ZADD NX 榜单、Redisson 锁释放 —— 均经 59 用例与 5000 并发压测验证。

必改（演示/交付红线）：**R1 精度 → R2 消息失败补偿 → R4 缓存失效 → C1 订单状态 → R5 死信/补偿 → C4 参数异常 → F1/F3/F4/F6**；其余按中低批次处理。
