# 项目开发计划与进度追踪

> 本文档记录项目开发过程中的进度、已完成工作、待办事项以及经验教训。

---

## 一、项目基本信息

| 项目 | 内容 |
|------|------|
| 项目名称 | 轻量级电商秒杀系统（E-commerce Flash Sale System） |
| 技术栈 | Spring Boot 3.3.4 / JDK 17 / MySQL 8.0 + MyBatis-Plus 3.5.7 / Redis 7.0 + Redisson 3.34.0 |
| 项目文档 | `docs/project.md`、`docs/developlan.md`、`docs/interface.md`、`docs/database.md`、`docs/plan.md`（进度追踪，含分日计划）、`docs/audit-report.md`、`docs/load-test-report.md`、`docs/storage.md`、`docs/notework.md` |
| 开发人员 | panda |
| 导师 | Claude Code |

### 沟通约定（必读）

1. **称呼**：项目代码编写过程中称呼为"panda"。
2. **语言**：始终使用简体中文回答，不混用英文单词（代码语法本身除外）。
3. **指导方式**：导师负责引导和检查，代码由 panda 亲手编写。导师不得直接替 panda 写代码。但遇到阻塞性问题（如配置错误、依赖冲突）时，导师可介入修复以保证进度。
4. **开发环境**：本地安装 MySQL 8.0，WSL Docker 运行 Redis 7.0，IntelliJ IDEA 作为 IDE。
5. **工作流程**：导师分步布置任务 → panda 编写代码 → 导师审查 → panda 修正 → 确认通过。每完成一个 Day 更新一次 plan.md 进度。
6. **测试验证**：每个功能模块完成后必须通过 Maven 测试验证（`mvn test`），保证 BUILD SUCCESS。
7. **问题记录**：遇到的技术问题和修复方案及时记录在 plan.md 的"经验教训"中，便于复盘。
8. **代码注释约定**：panda 在代码编写过程中会在代码中添加个人理解的知识笔记（教学式注释），用于记录学习过程。导师审查时应忽略这类注释，不作删除要求，仅关注逻辑正确性。
9. **plan.md 进度实时维护（导师负责）**：`docs/plan.md` 的进度状态由导师实时维护，panda 完成一个 Day 或任务后无需手动更新，只需告知导师进度即可。导师应在每个 Day/任务状态变化、修正完成后及时更新路线图、任务表和脚注，确保与代码实际进度一致。

---

## 二、整体路线图

```
■ 第 1 阶段：地基搭建与基础缓存（Day 1 ~ Day 7）
  ✅ Day 1 环境搭建  ✅ Day 2 实体与数据层  ✅ Day 3 公共组件  ✅ Day 4 商品缓存
  ✅ Day 5 缓存防护  ✅ Day 6 活动管理  ✅ Day 7 集成验收
✅ 第 2 阶段：秒杀核心与原子库存扣减（Day 1 ~ Day 5 全部完成，28 用例 BUILD SUCCESS）
✅ 第 3 阶段：高并发防护体系（Day 1 ~ Day 5 全部完成，40 用例 BUILD SUCCESS）
✅ 第 4 阶段：排行榜、前端与全链路压测（Day 1 ~ Day 5 完成，另含增强①图片存储、增强② AI 客服，59 用例）
✅ 第 4 阶段收尾：全量代码审计整改（R1~R5 / C1~C6 / E1~E6）+ E 卡收尾 + F/G 多账号 + S2/M 交付前收尾 + Day 6 复盘学习收口（《Redis 实战总结》/ notework 笔记；HEAD `c035ca8`，92 用例 BUILD SUCCESS）—— 4 个阶段全部完成
🔄 第 5 阶段：微服务架构演进（P0 ~ P6，进行中，方案与任务卡见 `docs/microservices-plan.md`）
  ✅ P0 边界治理（M1，2026-10-13）  ✅ P1 基础设施（P1-1 + P1-2 T1~T5 全收口）  ✅ P2 边缘抽取（auth / ai / 网关统一鉴权，`p2-done`）
  ✅ P3 商品与活动（独立库 + 应用 :8083/:8084/:8085 + 网关精确导流）  ✅ P4 核心交易链路（seckill/order 服务化 + 四权威库 + **旧库 `flash_sale` 已 DROP**）
  📋 P5 读模型收口（analytics 事件驱动，CQRS 完成）← **下一里程碑 M6**  ⏳ P6 质量与运维
```

---

## 三、第 1 阶段进度

### Day 1 — 环境搭建与项目初始化 ✅ 已完成

| 任务 | 状态 | 说明 |
|------|------|------|
| 搭建环境：MySQL 本机 + WSL Docker Redis | ✅ | 本地 MySQL + WSL Docker Redis 直接启动 |
| 初始化 SQL 脚本 | ✅ | `src/main/resources/db/schema.sql`（5 张表 + 种子数据） |
| pom.xml 依赖确认 | ✅ | 已确认完整 |
| application.yaml 配置 | ✅ | MySQL 数据源、Redis 连接、MyBatis-Plus 配置 |
| Redis/Redisson 配置类 | ✅ | `RedisConfig.java`、`RedissonConfig.java` |
| 环境连通验证 | ✅ | 应用启动成功，Tomcat 8080 端口正常 |

**Day 1 修复的问题：**
| 问题 | 修复方案 |
|------|----------|
| `GenericJackson2JsonRedisSerializer` 废弃警告 | 替换为 `RedisSerializer.json()` |
| `jackson-datatype-jsr310` 冗余依赖（Spring Boot 3.x 的 starter-web 已内置 Jackson 时间序列化支持） | 从 pom.xml 中删除 |
| `mysql-connector-j` 作用域优化 | 改为 `runtime` |
| `redisson-spring-boot-starter` 引用了 Boot 3.x 的旧类导致启动崩溃 | 替换为 `redisson` 核心库，删除错误的 `spring-boot-autoconfigure:4.0.5` 依赖 |
| Redis 认证失败（Redisson 默认发 AUTH 命令） | 条件设置密码，仅在配置了密码时才发送 AUTH |

---

### Day 2 — 实体模型与数据访问层 ✅ 已完成（2026-07-29）

| 任务 | 状态 | 说明 |
|------|------|------|
| 2.1 创建实体类 | ✅ | `Product.java`、`SeckillActivity.java`、`SeckillOrder.java` |
| 2.2 创建状态枚举 | ✅ | `ActivityStatusEnum`、`OrderStatusEnum`、`PreheatStatusEnum`、`ProductStatusEnum` |
| 2.3 创建 Mapper 接口 | ✅ | `ProductMapper`、`SeckillActivityMapper`、`SeckillOrderMapper` |
| 2.4 创建 XML 映射文件 | ✅ | 3 个文件：`ProductMapper.xml`、`SeckillActivityMapper.xml`、`SeckillOrderMapper.xml` |
| 2.5 Mapper 单元测试 | ✅ | `MapperTest.java` — 2 个测试方法通过（商品查询 + 活动查询） |

**Day 2 经验教训：**
1. **实体类设计关键点：**
   - `product` 和 `seckill_activity` 的 `id` 用 `IdType.INPUT`（手动输入）
   - `seckill_order` 的 `id` 用 `IdType.AUTO`（数据库自增）
   - 价格统一用 `Long`（单位：分），避免浮点精度问题
2. **枚举设计：** 统一使用 `code`/`description` 字段命名，使用 `@Getter` 注解简化代码，提供 `fromValue()` 静态方法
3. **Mapper 设计：** 继承 `BaseMapper<T>` 即可获得完整 CRUD，需要为每张表创建对应的 Mapper，但仅限当前阶段需要的表，不要超前创建
4. **pom.xml 注意事项：** MySQL 驱动使用 `com.mysql:mysql-connector-j`（新版），不要使用旧版的 `mysql:mysql-connector-java`
5. **JDBC 连接配置：** `characterEncoding` 参数必须用 Java 标准编码名 `UTF-8`，不是 MySQL 内部的 `utf8mb4`
6. **MyBatis-Plus 扫描：** 必须添加 `@MapperScan("com.ghb.ecommerceflashsalesystem.mapper")` 到入口类，否则 Mapper 不会被注册为 Spring Bean
7. **测试技巧：** `@Transactional` 注解可以让测试数据自动回滚，避免污染数据库

---

### Day 3 — 统一响应、异常处理与健康检查 ✅ 已完成（2026-08-01）

| 任务 | 状态 | 说明 |
|------|------|------|
| 3.1 统一响应体 `Result<T>` | ✅ | code/message/data/requestId/timestamp + 4 个静态工厂方法 |
| 3.2 状态码枚举 `ResultCode` | ✅ | 7 个业务状态码 + `fromValue()` 反查 |
| 3.3 分页响应体 `PageResult<T>` | ✅ | `of(Page)` / `of(records,total,pageNo,pageSize)` 两个工厂方法 |
| 3.4 异常处理 | ✅ | `BusinessException` + `GlobalExceptionHandler`（5 层处理链） |
| 3.5 请求追踪 `RequestIdUtil` | ✅ | 时间戳 + AtomicLong 序列号（线程安全） |
| 3.6 健康检查 `/api/health` | ✅ | 应用 + MySQL + Redis 探测，失败标记 DOWN |
| 3.7 Postman 验证 | ✅ | curl 验证三 UP，响应格式符合 interface.md |

**Day 3 经验教训：**
1. **静态方法用泛型**：静态工厂方法不能直接用类级泛型 `T`，必须声明方法级 `<T>`
2. **枚举 getter 命名**：Lombok `@Getter` 生成 `getCode()`，调用必须带括号（`rc.getCode()` 而非 `rc.getcode`）
3. **Maven 增量编译缓存异常**：代码已修复却仍报旧错误、或 Lombok 方法莫名找不到时，优先 `mvn clean` 全量重编
4. **端口冲突**：`Port 8080 already in use` 说明已有实例在跑，直接复用即可，无需重复启动
5. **两类校验异常**：`MethodArgumentNotValidException`（@Valid 请求体）与 `ConstraintViolationException`（参数/路径）是不同异常，都要单独处理
6. **约束校验异常消息**：拼接 `ConstraintViolation.getMessage()` 比用 `e.getMessage()` 简洁，后者带完整路径

### Day 4 — 商品查询接口与缓存旁路 ✅ 已完成（2026-08-02）

| 任务 | 状态 | 说明 |
|------|------|------|
| 4.1 商品视图对象 `ProductVO` | ✅ | `domain/vo/ProductVO.java`，含 `cacheHit` 标记字段，status 用 `ProductStatusEnum` |
| 4.2 商品缓存服务 `ProductCacheService` | ✅ | 缓存键 `product:detail:{productId}`，随机过期 300+random(60) 秒，`getProductFromCache` / `setProductToCache` |
| 4.3 商品服务 `ProductService` + `Impl` | ✅ | 缓存旁路：先查缓存 → 未命中查库 → 回写缓存 → 返回，带 cacheHit 标记与查库/查缓存日志 |
| 4.4 商品查询控制器 `ProductController` | ✅ | `GET /api/products/{productId}`，商品不存在返回 40004，运行验证通过 |
| 4.5 管理端控制器 `AdminProductController` | ✅ | `POST /api/admin/products` 创建/更新商品，写操作后删除对应缓存 |
| 4.6 商品请求 DTO `ProductRequest` | ✅ | `@NotBlank` / `@NotNull` 校验，status 用 `ProductStatusEnum`，productId 可选（创建/更新判定） |
| 4.7 接口验证与测试 | ✅ | 7 个场景全部通过（创建/首查 cacheHit=false/二次 cacheHit=true/更新清缓存/不存在 40004/参数缺失 40001）；`mvn test` BUILD SUCCESS |

**Day 4 设计决策（任务布置阶段确定）：**

1. **`seckillPrice` 不引入**：interface.md 示例含 `seckillPrice`，但秒杀价属于 `seckill_activity` 表，`Product` 实体无此字段，本轮商品模块以实体为准。
2. **status 用枚举输出**：`ProductVO.status` 声明为 `ProductStatusEnum` 类型，Jackson 序列化为 `"ON_SHELF"` / `"OFF_SHELF"`，通过 `fromValue` 与数据库 `int` 互转。
3. **创建/更新判定**：请求体带 `productId` → `updateById`；不带 → 应用层生成新 id 后 `insert`（`id` 为 `IdType.INPUT`）。
4. **缓存键常量**：新建 `CacheKeyConstant` 收敛 `product:detail:` 前缀，Day 6 活动缓存键复用同一常量类。

**验收标准：**

- 首次查询商品：日志显示查库，响应 `cacheHit: false`
- 第二次查询同商品：日志显示查缓存，响应 `cacheHit: true`
- 管理端更新商品后旧缓存被清除（再次查询应回库重建）
- 查询不存在的商品返回 `code: 40004`
- 全量 `mvn test` 保证 BUILD SUCCESS

**Day 4 边界：** 缓存穿透防护（空值缓存）与缓存击穿防护（互斥锁）属 Day 5，本次不实现；查库未命中直接返回 40004。

**Day 4 经验教训：**

1. **`@MapperScan` 必须显式指定 mapper 包**：无参 `@MapperScan` 默认扫描入口类所在包及全部子包，会把业务 Service 接口（如 `ProductService`）误注册为 MyBatis Mapper 代理，调用时抛 `Invalid bound statement (not found)`。修复：`@MapperScan("com.ghb.ecommerceflashsalesystem.mapper")` 只扫描 mapper 包。（印证 Day 2 第 6 条的后果）
2. **Redis 序列化 `LocalDateTime` 必须注册 `JavaTimeModule`**：`RedisSerializer.json()` 内部 ObjectMapper 不含 `JavaTimeModule`，序列化实体中的 `LocalDateTime` 字段（如 `Product.createdAt`）报错。修复：注入 Spring Boot 自动配置的 `ObjectMapper`（已含 JavaTimeModule），`copy()` 后调用 `activateDefaultTyping(NON_FINAL, As.PROPERTY)` 构造 `GenericJackson2JsonRedisSerializer`。**必须开启 DefaultTyping**，否则反序列化无法还原具体类型（`instanceof Product` 判断会失效）。
3. **拼写 `ProductVo` vs `ProductVO`**：Java 类名区分大小写，`ProductVo` 类不存在导致"找不到符号"；同时接口与实现返回类型不一致（实体 `Product` vs 视图 `ProductVO`）会使 `@Override` 失败。接口契约统一返回 `ProductVO`。
4. **数据库种子数据与 schema.sql 不一致导致测试失败**：`MapperTest` 断言与 schema.sql 均为 `total_stock=1000`，但库里实际是 `100`（库比 schema 旧），测试报 `expected: <1000> but was: <100>`。修复：单条 `UPDATE` 修正种子数据（或重跑 schema.sql，其 `CREATE TABLE IF NOT EXISTS` + `INSERT ON DUPLICATE KEY UPDATE` 幂等安全）。
5. **`@Valid` 触发校验 + 请求体反序列化失败的区分**：`POST /api/admin/products` 用 `@Valid @RequestBody`，JSON 反序列化失败返回 40001"请求体格式错误"（`HttpMessageNotReadableException`），参数校验失败返回 40001 + 具体字段消息——两者都走全局异常处理，无需重复捕获。

### Day 5 — 缓存防护（缓存穿透 + 缓存击穿）✅ 已完成（2026-08-03）

| 任务 | 状态 | 说明 |
|------|------|------|
| 5.1 缓存穿透防护（空值缓存） | ✅ | `ProductCacheService` + `ProductServiceImpl`：查库未命中写空值标记（TTL 60s），命中标记直接返回 40004 不再打库（curl + 单测验证） |
| 5.2 缓存击穿防护（互斥锁） | ✅ | Redisson 分布式锁：锁内双重检查 + `tryLock` 双参数 + 超时兜底 + 中断处理，8 线程并发测试验证仅 1 次查库 |
| 5.3 单测与验收 | ✅ | `ProductCacheProtectionTest`（穿透/击穿 2 测试）+ 原 4 测试全绿，`mvn test` BUILD SUCCESS |

**Day 5 设计决策（任务布置阶段确定）：**

1. **空值标记存储形式**：使用空字符串 `""` 作为空值缓存标记，`CacheKeyConstant` 新增空值 TTL 常量（60 秒）。
2. **读取三态区分**：`ProductCacheService` 读取缓存需区分"未命中 / 命中空值标记 / 命中真实商品"三种情况；现有"非 `Product` 类型即删除"的防御分支需调整，避免误删空值标记。
3. **穿透分支位置**：`ProductServiceImpl.getProductDetail` 查库前先判断是否命中空值标记，命中直接返回 null（映射 40004）；查库未命中先写空值标记再返回 null。
4. **5.1 不引入锁**：保持"查缓存 → 判空值 → 查库 → 回写"线性结构，互斥锁留待 5.2 引入，便于单独验证穿透逻辑。

**验收标准（5.1）：**

- 第一次查询不存在的 productId：日志显示查库 → 返回 40004
- 第二次查询同一 ID：日志显示命中空值缓存，不出现查库日志 → 仍返回 40004
- 正常商品查询（cacheHit false→true）行为不受影响
- 删除键 / 更新商品的缓存清理逻辑不变（空值标记一并清除）

**Day 5 经验教训：**

1. **Redis 数据库选择与排查陷阱（database: 1）**：应用配置 `spring.data.redis.database: 1`，与默认 db0 隔离。排查时 redis-cli 默认连接 db0，会误判"键不存在"（DBSIZE=0），实际键在 db1——查 Redis 须用 `redis-cli -n 1`。同理，外部工具（如 Redis 桌面管理器）若连不同库，会看到互相隔离的数据。
2. **空值标记不能走"类型异常删除"分支**：`getProductFromCache` 原对非 `Product` 类型的值一律删除，空值标记（String `""`）一读就被删，穿透保护形同虚设。空值标记需独立分支：命中返回 null 且不删除。
3. **空值分支禁止强转 `(Product) value`**：空值标记是 String 不是 Product，强转编译能过但运行时会抛 `ClassCastException`。命中空值标记应返回 null。
4. **锁键也要收敛到 `CacheKeyConstant`**：5.2 初版把锁前缀硬编码在 Service 里，常量成了死代码。键前缀统一走常量类，避免散落。
5. **击穿防护核心是"锁内双重检查"**：等锁期间其他线程可能已回源，拿到锁必须先查缓存/空值，命中直接返回；`tryLock(waitTime, leaseTime)` 两个参数都设（防死锁自动释放），释放前用 `lock.isHeldByCurrentThread()` 判断；`tryLock` 等待会被中断，需处理 `InterruptedException` 并恢复中断位。
6. **测试数据漂移（反复踩坑）**：管理端接口测试拿商品 1（MapperTest 断言对象）做更新，会把种子数据改掉导致 `mvn test` 失败（本次原价 29900→19900、库存 1000→100）。习惯：验证更新接口用专属测试商品，或测完恢复种子数据。
7. **curl 发送中文请求体易踩 40001**：Windows 终端按 GBK 编码发送中文，Jackson 按 UTF-8 解析 → `HttpMessageNotReadableException` → "请求体格式错误"，请求根本不进业务层。用 Postman / UTF-8 文件（`--data-binary @file`）发送，或先 `chcp 65001`。
8. **"Redis 真实 + Mapper Mock"的测试组合**：缓存/锁是被测对象须用真实 Redis；DB 查询只需数次数+控制行为，用 `@MockBean` 替换 Mapper。`verify(mapper, times(1)).selectById(...)` 是并发断言的利器；并发测试用 `CountDownLatch` 同步 + `doneLatch.await` 加超时，线程内异常要兜住避免挂死。

### 后续任务计划（Day 6 ~ Day 7）

| 天 | 主要内容 | 前置依赖 | 状态 |
|----|----------|----------|------|
| Day 6 | 活动管理、缓存预热、活动查询与校验接口 | Day 5 ✅ | ✅ 完成 |
| Day 7 | 集成测试、问题修复、阶段验收 | Day 5 + Day 6 | ✅ 完成 |

**Day 6 细分任务进度：**

| 任务 | 状态 | 说明 |
|------|------|------|
| 6.1 活动相关 VO/DTO | ✅ | `SeckillActivityVO`、`ActivityRequest`、`ActivityCheckResponse` 已创建 |
| 6.2 活动缓存服务 `SeckillCacheService` | ✅ | `preheatActivity` 已写，导师审查修正 3 处：库存键前缀（`seckill:stock:`）、失败分支改 try-catch 并标记 `PREHEAT_FAILED`、删多余 import（2026-08-24 复核时又删净残留的 `Calendar` import）；2026-08-25 全量 clean test-compile BUILD SUCCESS |
| 6.3 活动服务 `SeckillActivityService` + 实现 | ✅ 已完成 | 3 方法（createActivity / getActivityDetail / checkActivity）编写完成；审查发现 3 处问题：SeckillCacheService 两个读方法编译错误（5 处类型误用，导师代修正并保留错误代码注释）、Impl 自注入循环依赖、checkActivity 缺 break（panda 自行修正）；2026-08-25 全量 clean test-compile BUILD SUCCESS |
| 6.4 管理端活动控制器 `AdminSeckillController` | ✅ 已完成 | 创建 `/api/admin/seckill/activities` + 预热 `/{activityId}/preheat`；创建接口更新场景返回真实状态（导师代理修正，原固定 NOT_STARTED 保留为注释）；全量 clean test-compile BUILD SUCCESS（2026-08-25） |
| 6.5 活动查询控制器 `SeckillActivityController` | ✅ | 查询详情 `GET /api/seckill/activities/{activityId}` + 校验 `GET .../check?userId=`；对应 interface.md 4.6/4.7；panda 修正两处：userId 绑定 `@PathVariable`→`@RequestParam`、`checkActivity` 传参顺序（activityId 在前）；全量 clean test-compile BUILD SUCCESS |
| 6.6 Postman 全流程验证 | ✅ | 新建未来时间活动 → 预热接口 4.5 验证通过；活动详情/校验接口随 6.5 编译验证通过 |

> 注：Day 6 从任务 6.2 起进度由导师实时维护到本表，避免与文档脱节。

**Day 6 经验教训：**

1. **种子活动时间过期导致预热报"活动已结束"**：schema.sql 里活动 1 时间是 `2026-07-27 20:00~21:00`，跑预热接口会命中 `startTime/endTime` 判定 `endTime.isBefore(now)` → 返回 40001"活动已结束，无法预热"。这不是代码 bug，是测试数据过期。正确做法：用创建接口 `POST /api/admin/seckill/activities` 新建一条 endTime 在未来的活动再预热（顺带验证创建接口），或单条 `UPDATE` 改活动时间。
2. **项目无用户表，userId 由调用方透传**：schema.sql 5 张表没有 user 表，`user_id` 只存在于 `seckill_order`/`seckill_message_log` 作为订单归属字段，代表上游账号系统透传的业务编号。校验/幂等/排行榜都用它，无需也不能从本库查用户档案。
3. **`@PathVariable` vs `@RequestParam` 语义**：`@PathVariable` 从 URL 路径占位符取（RESTful 资源 id），`@RequestParam` 从 `?key=value` 查询串取（筛选/操作人上下文）。userId 属调用方上下文，用 `@RequestParam`，与 interface.md 4.7 `?userId=` 一致。路径变量默认必填、参数名不一致需 `@PathVariable("name")` 显式指定。
4. **两个同为 Long 的参数传参顺序颠倒编译检测不到**：`checkActivity(activityId, userId)` 接口里两个参数都是 `Long`，调用写成 `(userId, activityId)` 编译直接通过，但运行时查错用户/判错资格。核对接口签名与调用处参数顺序是审查必查项。
5. **Redisson 启动断连日志非 bug**：应用启动后偶发 `IOException: 你的主机中的软件中止了一个已建立的连接`，是连接池预建连接撞上 WSL Docker 端口转发的 RST（连接重置）导致，Redisson 会自动重连，不影响功能。缓解方案 B：`RedissonConfig` 加 `setIdleConnectionTimeout(30000)`（默认 10 秒）让空闲连接存活更久、建立/回收更少。

---

### Day 7 — 集成测试、问题修复与阶段验收 ✅ 已完成（2026-09-01）

| 任务 | 状态 | 说明 |
|------|------|------|
| 7.1 全量 Postman 接口验证 | ✅ | 7 个接口（健康检查/商品管理/商品查询/活动管理/活动预热/活动查询/活动校验）手工验证通过 |
| 7.2 集成测试 `Phase1IntegrationTest` | ✅ | 覆盖预热成功、防重预热、命中缓存不查库、未预热回源 DB、活动校验（进行中/已结束）、结束拒绝预热。**导师审查发现 7 处断言/逻辑问题**，panda 全部修正后 9 用例转绿 |
| 7.3 缓存穿透集成测试 | ✅ | `testCachePenetration()`：独立 ID（999999L）连续查询 100 次，`verify(times(1))` 只打 1 次库，断言空值标记写入 |
| 7.4 缓存击穿集成测试 | ✅ | `testCacheBreakdown()`：独立 ID（888888L），300ms 慢查询拉开并发窗口，8 线程 CountDownLatch，`verify(times(1))`，断言锁释放+缓存回写 |
| 7.5 Bug 修复 | ✅ | 修复测试发现的问题（含 7.2 的 7 处断言/逻辑问题） |
| 7.6 application.yaml 生产级配置复查 | ✅ | 连接池/超时/日志级别复核完成 |
| 7.7 验收报告 `docs/phase1-review.md` | ✅ | 阶段验收报告输出 |

**Day 7 经验教训：**

1. **断言类型必须与实际取出类型对齐**：预热写 `seckillPrice` 为 Long，但经 GenericJackson 序列化往返后 HashSet 取出变 Integer，断言 `isEqualTo(9900L)` 失败，改 `isEqualTo(9900)`。序列化往返可能改变数值装箱类型，断言对齐实际类型。
2. **`redisTemplate.delete()` 清理键要判「非空才删」**：`if (keys != null && keys.isEmpty())` 写反会导致 Redis 键（含空值标记）永远清不掉，污染相邻测试。正确 `!keys.isEmpty()`。
3. **`reset(mock)` 会连 stub 一起清掉**：`reset` 不仅清调用记录，还会清掉 `thenReturn`。测试里 reset 后 mock 返回 null，若再 verify 调用次数会与意图不符。命中缓存场景应 `verify(times(0))` 且配合 reset，或不用 reset 直接统计预热那 1 次。
4. **Redis 未启动时全量测试会「全崩」是环境问题，非代码 bug**：`Unable to connect to Redis server: 6379` → ApplicationContext 启动失败 → 15 个测试全 Errors（0 Failures），连之前通过的也连带。Root cause 是 WSL Docker 的 Redis 容器没起/没映射 6379。排查 `docker ps` → `redis-cli -n 1 ping`（应 PONG）。这是环境问题，不体现在单个断言里，容易误判成代码回归。
5. **全量 `mvn test` 才能发现"单类过、整体挂"的连带问题**：单跑 `Phase1IntegrationTest` 是 9/9 绿，但全量因别的类 / 环境原因挂。Day 收尾必须以全量 BUILD SUCCESS 为准，不能只看单个测试类。

---

### 第 2 阶段开发计划（秒杀核心与原子库存扣减）

> 阶段目标：实现真正的高并发秒杀核心 `/api/seckill/execute`，用 Redis Lua 脚本保证库存原子扣减、杜绝超卖，并用并发测试/JMeter 验证「库存精确到 0、永不超卖」。产出物对应本文档「第 2 阶段」小节（`docs/day.md` 系早期分日文档旧称，从未入库，其内容已并入本文档演进为路线图）。

- **现有基础（第 1 阶段遗产）**：`seckill:stock:{activityId}` 库存键 + `preheatActivity` 预热 ✅、`SeckillActivityService.checkActivity` 时间/状态校验雏形 ✅、`SeckillOrder` 实体与 Mapper ✅、`seckill:lock:` 锁前缀常量 ✅、`/api/seckill/check` 校验接口 ✅。
- **本轮缺口**：`resources/lua/` 目录为空（Day 1 起补）、`/seckill/execute` 接口不存在、`seckill:user:{activityId}:{userId}` 幂等键只定义了常量未真正使用。

#### Day 1 — 库存扣减 Lua 脚本与 RedisScript 配置

| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 1.1 | 编写库存扣减 Lua 脚本 `decr_stock.lua` | ✅ | 已写 `src/main/resources/lua/decr_stock.lua`：键不存在/无法转数字/库存≤0 返回 -1，否则 `DECRBY` 返回扣后值 |
| 1.2 | 配置 `RedisScript<Long>` Bean | ✅ | `RedisConfig.decrStockScript`：`DefaultRedisScript<Long>` + `ClassPathResource("lua/decr_stock.lua")` + `resultType=Long` |
| 1.3 | Lua 脚本单元测试 | ✅ | `LuaStockDeductionTest` 4 场景全绿（真实 Redis）：正常扣 100→99 / 库存 0 返 -1 不扣 / 键不存在返 -1 / **10 线程并发抢 50 库存：成功 50、拦截 10、库存归 0 无超卖** |

**Day 1 验收标准：** 脚本扣减原子、库存趋 0 不转负；单测 3 场景全绿。—— ✅ 达成（2026-09-03，全量 `mvn test` 19 用例 BUILD SUCCESS）

**Day 1 经验教训：**
1. **并发防超卖测试的"请求量要大于库存"**：初版 10 线程各只扣 1 次 = 共 10 次请求，50 库存根本扣不完（断言期望 0 实际 40）。正确姿势是让线程 `while(true)` 循环抢购直到脚本返回 -1 才退出，这样成功数恰=库存、超额请求被拦计数、库存精确归零——并发断言才有意义。
2. **`redisTemplate.keys` 模式必须带通配符**：`keys("seckill:stock:")` 匹配不到 `seckill:stock:100`，需 `keys("seckill:stock:*")`；配套"非空才删"条件 `!keys.isEmpty()` 写反则永远清不掉（Day 7 教训 2 复现，本项目反复踩）。
3. **测试中断残留键靠 setUp 全量清键兜底**：多场景共用 `stockKey` 时，一旦某用例异常中断残留键，后续"键不存在"场景会被污染间歇失败，`setUp` 清理不可省。
4. **本机 Maven 命令损坏（阻塞性环境问题，导师介入修复）**：用户级 `MAVEN_HOME=D:\Maven\apache-maven-3.9.4` 已损坏/移动，`mvn`/`mvnw` 均报 `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`。可用 Maven 3.9.16 在 wrapper 缓存 `~/.m2/wrapper/dists/apache-maven-3.9.16/<hash>/`。绕行启动方式（Git Bash；路径须用 `C:/...`，`/c/...` 传给 Windows 程序会转错）：
   ```bash
   MH='C:/Users/ghb19/.m2/wrapper/dists/apache-maven-3.9.16/<hash>'
   java -classpath "$MH/boot/plexus-classworlds-2.11.0.jar" \
     "-Dclassworlds.conf=$MH/bin/m2.conf" "-Dmaven.home=$MH" \
     "-Dmaven.multiModuleProjectDirectory=<工程目录>" \
     org.codehaus.plexus.classworlds.launcher.Launcher test
   ```

#### Day 2 — 秒杀核心接口 `/api/seckill/execute`

| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 2.1 | 请求 DTO `SeckillRequest` | ✅ | `domain/dto/request/SeckillRequest.java`：`activityId`/`userId` `@NotNull` 带中文 message；`productId` 保留为可传非必填，类注释写明取舍（对齐接口文档 4.8 契约 + 冗余透传，服务端由活动推导商品信息） |
| 2.2 | `SeckillService` + 实现 | ✅ | `service/seckill/SeckillService.java`（interface）+ `service/impl/SeckillServiceImpl.java`。链路：缓存优先查活动（未命中回源 DB，不存在 NOT_FOUND）→ 时间窗口动态校验（未开始/已结束 40001）→ status 仅辅助拦 CANCELLED → Lua 原子扣库存 → 返回 `result=QUEUED`；`-1` 抛 `OUT_OF_STOCK(40902)` |
| 2.3 | `SeckillExecuteController` | ✅ | `controller/seckill/SeckillExecuteController.java`：`POST /api/seckill/execute`（对应 interface.md 4.8），`@Valid @RequestBody`，统一 `Result` 包装（本阶段不伪造 orderNo，留待异步下单阶段补齐） |
| 2.4 | 接口验证 | ✅ | 预热后执行四场景验证通过（panda 汇报）：活动进行中连续扣到 0 / 已结束 40001 / 售罄 40902 / 参数缺失 40001 |

**Day 2 验收标准：** 接口链路走通，库存扣减反映到 `seckill:stock:{id}`；`mvn test` 保证 BUILD SUCCESS。—— ✅ 达成（2026-09-03，全量 `mvn test` 19 用例 BUILD SUCCESS，既有用例无回归）

**Day 2 经验教训：**

1. **Service 层结构必须遵循「接口 + Impl」约定**：v1 曾定义成 `abstract class SeckillServer`，且类内同时出现两个同签名 `execute`（一个有方法体、一个 abstract），Java 不允许同签名方法重复定义，编译直接失败。命名也要用业务语义的 Service（`Server` 含义是"服务器"），与 `ProductService`/`SeckillActivityService` 保持统一。
2. **扣库存的 key 必须与预热 key 完全一致**：预热库存写入 `SECKILL_STOCK_PREFIX`（`seckill:stock:`），扣减时若误拼成活动 Hash 前缀 `SECKILL_ACTIVITY_PREFIX`，Lua 对 Hash 键执行 `GET` 会报 `WRONGTYPE`，接口直接 500、库存永不扣减（Day 6 已踩过"库存是独立 String 键"的同类错误，本次再次确认）。
3. **秒杀主链查活动不要直接打库**：`execute` 第一步应复用 `SeckillCacheService` 缓存优先、未命中再 `selectById` 回源——绕过缓存等于把秒杀校验又压回数据库，与预热设计背道而驰。
4. **活动开放判定以缓存时间窗口动态推导，不依赖 `status` 快照**：管理端建的活动 `status` 恒为创建值（0），预热写入缓存的是当时快照，**没有任何机制自动翻转为 RUNNING**。若强制 `status == RUNNING` 才放行，预热后到点的活动永远抢不到、验收无法构造场景。正确姿势：`now < startTime` → 未开始；`now ≥ endTime` → 已结束；`status` 仅作辅助（显式 `CANCELLED` 提前拦截），售罄交给 Lua 兜底。
5. **错误码语义要对**：活动存在但未开放应返回 `PARAM_ERROR(40001)`，不是 `NOT_FOUND(40004)`（"资源未找到"仅用于活动/商品本身不存在）。
6. **时间边界用 `!now.isBefore(endTime)` 而非 `isAfter`**：`now` 达到 `endTime` 那一刻即视为已结束。
7. **`ActivityStatusEnum.fromValue` 对非法值返回 `null`**：紧接着调 `statusEnum.getDescription()` 会空指针，使用前必须判空。
8. **导师代修代码按复盘注释保留原错误**：本轮 panda 审查后主动要求导师直接代改，旧错误写法（结构、直接查库、强 status 校验、错误库存前缀）以 `/* */` 注释保留在新文件对应位置，与正确代码就近对照，便于复习。

#### Day 3 — 并发防超卖验证（JMeter / 并发测试）

| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 3.1 | 并发防超卖集成测试 | ✅ | `SeckillExecuteConcurrencyTest`：**200 并发抢 100 库存**（请求量 > 库存，断言更有意义），预热走真实 `preheatActivity`，并发调 `SeckillService.execute` 断言成功恰 100、拒绝恰 100（OUT_OF_STOCK）、Redis 最终库存 0 无超卖；mock 活动 `status` 故意用 `NOT_STARTED`，顺带回归 Day 2 时间窗设计（不依赖 DB status=RUNNING 也能抢） |
| 3.2 | JMeter 脚本（可选） | ⏸ 延后 | 与 panda 商定：延后到第 4 阶段全链路压测（真实 HTTP 并发 + 订单数据）再补 `scripts/jmeter/` 脚本，本日不做 |
| 3.3 | 遗留 bug 修复 | ✅ | `Phase1IntegrationTest.cleanRedisKeys` 判空条件写反（`keys.isEmpty()` → `!keys.isEmpty()`）顺手修正，测试键清理恢复正确 |

**Day 3 验收标准：** 并发超卖防护实证——库存精确到 0，成功订单数不超库存。—— ✅ 达成（2026-09-03，并发测试成功 100 / 拒绝 100 / 最终库存 0；全量 `mvn test` 20 用例 BUILD SUCCESS，既有用例无回归）

**Day 3 经验教训：**

1. **删除私有方法要连调用点一起删，否则报"找不到符号"**：只删 `reset()` 方法定义、保留 `reset(mock)` 调用，编译器找不到匹配方法；此时 IDE 自动导入的候选可能张冠李戴——本次误导入 `org.awaitility.Awaitility.reset`（无参方法，用于重置轮询配置），与带参调用签名不匹配照样编译失败。看清方法与参数签名再选 import。
2. **`@MockBean` 默认每个测试方法后自动重置（MockReset.AFTER），无需手动 reset**：空壳 `reset()` 方法无任何作用还误导后来者；真正需要时用 `Mockito.reset(mock)`（会连 stub 一起清，必须重新打桩）。
3. **并发断言要"请求量 > 库存"才有意义**：200 并发抢 100 库存，成功必须恰等于库存、拒绝恰等于超额；若请求量 == 库存，防超卖缺陷可能被掩盖（Day 1 教训在服务层链路的再次印证）。
4. **mock 活动 `status` 用 `NOT_STARTED` 也能抢通**：实际验证了 Day 2 的设计决策——execute 以缓存时间窗口动态判定开放状态、status 仅辅助拦 CANCELLED，预热后无需手工把 DB status 改成 RUNNING。测试 mock 尽量贴近真实业务形态，能顺带覆盖决策回归。
5. **测试键清理的 `!keys.isEmpty()` 是本项目反复踩点**：本次借 Day 3 把 Phase1IntegrationTest 里遗留的反写条件一并修正（Day 7 教训 2 / Day 1 教训 2 同源）。

#### Day 4 — 用户维度防重（SETNX + 凭证令牌）

| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 4.1 | 幂等常量 + SETNX 令牌 | ✅ | `CacheKeyConstant` 新增 `SECKILL_USER_PREFIX`（`seckill:user:`）与 `SECKILL_USER_TOKEN_TTL`（30 分钟）；`execute` 在时间窗/status 校验通过后、Lua 扣库存前执行 `setIfAbsent`，建成功才继续。期间令牌键曾误拼 `SECKILL_ACTIVITY_PREFIX`（活动前缀），导师代改为用户前缀并留复盘注释 |
| 4.2 | 重复秒杀拦截 + 售罄回滚 | ✅ | `setIfAbsent` 返回 false → 抛 `BusinessException(DUPLICATE_PURCHASE=40901)`「请勿重复秒杀」；**售罄回滚设计**：Lua 返回 -1（库存不足）时先删除刚建的令牌再抛 `OUT_OF_STOCK`——"没抢到不锁死 30 分钟"，已抢到判定以订单落库为准 |
| 4.3 | 幂等防重集成测试 | ✅ | `SeckillUserDedupTest` 5 用例全绿：单用户防重（第 2 次 40901）/ 不同用户互不影响 / 令牌 TTL>0 且 ≤1800s / 售罄回滚令牌不残留 / **同用户 50 线程并发仅 1 次成功**（SETNX 原子防重） |

**Day 4 验收标准：** 同一用户不能重复下单，令牌键 TTL 生效。—— ✅ 达成（2026-09-03，`mvn test` 25 用例 BUILD SUCCESS）

**Day 4 经验教训：**

1. **键前缀第三次混用**：本次幂等令牌键误拼 `seckill:activity:`，应为 `seckill:user:`。项目里三类键前缀（activity Hash / stock String / user 令牌）极易混，拼键前先核对 `CacheKeyConstant`，与预热写入方保持一致（Day 2 库存键、Day 4 令牌键同源教训）。
2. **测试方法缺闭合 `}` 会把后续用例"吞"进方法体**：`testTokenRollbackOnSoldOut` 少写一个 `}`，导致用例 5 的 `@Test` + 方法定义嵌套在方法内，编译报错且难定位。写完一个方法立刻闭合（`}` 对齐缩进是信号），或用 IDE 格式化让结构错误显形。
3. **断言先想"刚写完的期望值"**：令牌刚 SETNX 成功，TTL 应接近 1800 秒，却断言 `isEqualTo(0L)`——期望值本身写反了。写断言前先在脑内过一遍状态时序。
4. **售罄回滚是防重的必要补丁**：SETNX 建令牌 → Lua 扣减失败若不回滚，用户"没抢到却锁 30 分钟"，体验与语义都错。回滚语义会原样迁入 Day 5 的整合 Lua 脚本。
5. **Redis 假死排查法（环境问题）**：容器进程在、TCP 端口通，但所有命令 PING 超时（全量测试表现为 25 用例全 Errors、0 Failures，Spring 上下文起不来）。排查：`wsl -d redis -- docker exec my-redis redis-cli -p 6379 -n 1 ping` 无 PONG 即假死 → `docker restart my-redis` 恢复。Day 7 教训 4 补充：除"容器没起"外还有"容器假死"这一形态。
6. **SETNX 原子性可用并发验证**：同一 userId 50 线程并发 execute，恰好 1 次成功 + 49 次 `40901`，证明防重入口无竞态。

#### Day 5 — 令牌检查并入 Lua（原子整合）与阶段验收

| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 5.1 | Lua 整合脚本 `seckill_execute.lua` | ✅ | 单脚本原子完成「SETNX 令牌 + 库存检查扣减 + 售罄自动回滚」，头注释写明原子性理由（消除跨命令中间态）；`RedisConfig` 注册 `seckillExecuteScript` Bean；契约：KEYS[1] 库存 / KEYS[2] 令牌，返回 `>=0` 剩余库存 / `-1` 售罄（脚本内 DEL 令牌）/ `-2` 重复秒杀 |
| 5.2 | execute 改用整合脚本 | ✅ | `SeckillServiceImpl` 移除分步 `setIfAbsent`（整段保留注释复盘），统一走脚本；`>=0`→QUEUED、`-1`→`OUT_OF_STOCK`、`-2`→`DUPLICATE_PURCHASE`、`null`→`SYSTEM_ERROR` 兜底；脚本参数改传数值（避免 String 序列化带引号致 `tonumber` 失效） |
| 5.3 | 阶段全量验收 | ✅ | 新增 `SeckillExecuteScriptTest` 3 场景（成功 9 / 重复 -2 / 售罄回滚 -1）；`SeckillExecuteConcurrencyTest`（200 并发防超卖）+ `SeckillUserDedupTest`（防重/回滚）换脚本后回归全绿；全量 `mvn test` 28 用例 BUILD SUCCESS |

**Day 5 验收标准：** 高并发下无超卖且防重不失效；全量测试通过；第 2 阶段验收达成。—— ✅ 达成（2026-09-04）

**✅ 第 2 阶段整体验收小结：**

- **接口**：`/api/seckill/execute` 完整链路落地（查活动缓存优先 → 时间窗动态校验 → status 辅助 → Lua 原子「建令牌 + 扣库存 + 失败回滚」）；
- **防超卖实证**：`SeckillExecuteConcurrencyTest` 200 并发抢 100，成功恰 100、拒绝恰 100、库存归 0；
- **防重实证**：`SeckillUserDedupTest` 同用户 50 并发仅 1 成功（`SETNX`/Lua `EXISTS` 原子）；
- **脚本级验证**：`SeckillExecuteScriptTest` 三返回码直测；
- **历史遗留清零**：`Phase1IntegrationTest.cleanRedisKeys` 判空反写修复；
- 第 2 阶段共沉淀 Lua 脚本 2 个、服务 1 组、测试 4 个类，全量 **28 用例 BUILD SUCCESS**。

**Day 5 经验教训：**

1. **脚本 ARGV 传 String 会被序列化器加引号**：`String.valueOf(ttl)` 经 GenericJackson 编码成 `"1800"`（含引号），Lua `tonumber` 得 nil，只能靠脚本 `or 1800` 兜底，常量一改就静默出错。脚本参数应传 `Integer/Long` 数值（序列化无引号），Day 1 已验证该路径。
2. **注释块别吞掉方法收尾 `}`**：用 `/* */` 注释 Day 4 旧逻辑时把 `execute` 的闭合大括号一并注释，方法体一路延伸到 `getActivityVO`，导致"方法内嵌套方法"编译失败（`非法的表达式开始`）。注释保留旧代码时，先确认结构性括号没被包进去。
3. **脚本返回值的每个码都要映射到业务分支**：`-2`（重复秒杀）曾落入兜底 else 变 `SYSTEM_ERROR(50000)`；脚本定契约后，Java 侧 `>=0/-1/-2/null` 逐一处理，缺一不可。
4. **测试工具类 import 要看清来源**：误用 `org.assertj.core.util.Arrays.asList`（参数语义不同）编译失败，应使用 `java.util.Arrays`。
5. **脚本级测试别预建被测前置状态**：`testScriptSoldOut` 若先建令牌，脚本 `EXISTS` 第一步就返回 `-2`，永远测不到售罄分支；正确姿势是让脚本自己走完「建令牌 → 库存 0 → 回滚 → -1」。
6. **Lua 拼写陷阱**：`false` 误写 `flase` 是未定义变量，条件恒假成死代码（本次恰好被后续 `tonumber(false)→nil` 兜住，纯属侥幸）。

---

### 第 3 阶段开发计划（高并发防护体系：分布式锁 / 限流 / Redis Stream 异步下单）

> 阶段目标：把 `/api/seckill/execute` 从"预扣库存即返回"升级为完整秒杀闭环：**滑动窗口限流防刷 → Lua 原子扣减（已具备）→ Redis Stream 消息削峰 → 消费者异步落订单（DB 唯一键兜底）→ 消费可靠性（ACK/重试/死信 + 消息日志）→ Redisson 锁守护写入口并发**。

- **现有基础（第 2 阶段遗产）**：`execute` Lua 原子「令牌+扣减+回滚」无超卖防重 ✅、`decr_stock.lua`/`seckill_execute.lua` + RedisScript Bean ✅、`seckill_order`/`seckill_message_log`/`seckill_activity_snapshot` 表已建且 `SeckillOrder` 实体已建 ✅、`CacheKeyConstant` 前缀收敛 ✅。
- **本轮缺口**：限流键/脚本/服务不存在（`RATE_LIMITED(42900)` 常量未用）；Redis Stream 生产者/消费者与 `seckill:order:stream`/`seckill:order:dead:stream` 常量未建；`SeckillOrderMapper` 未真正落单、`execute` 无真实 `orderNo`；`seckill_message_log` 无实体与写入；Redisson 锁尚未用于秒杀写入口。

| 天 | 主题 | 状态 | 核心产出 |
|---|---|---|---|
| Day 1 | 滑动窗口限流 | ✅ | `rate_limit.lua`（ZSet 清过期成员 + 计数）接入 `execute` 最前置，超限返回 `42900` |
| Day 2 | Stream 生产者削峰 | ✅ | `execute` 扣库存成功后发布消息到 `seckill:order:stream`，返回「排队中 + orderNo」 |
| Day 3 | 消费者异步落单 | ✅ | 消费者组读 Stream → 写 `seckill_order`（`uk_activity_user` 唯一键兜底）→ XACK |
| Day 4 | 消费可靠性 | ✅ | 失败重试、死信 `seckill:order:dead:stream`、`seckill_message_log` 落库追踪 |
| Day 5 | 分布式锁落地 + 阶段验收 | ✅ | Redisson 锁防护预热/库存重置等写入口并发；全量回归 + 验收 |

### 第 3 阶段 Day 1 进度 — 滑动窗口限流 ✅

| 任务 | 状态 | 说明 |
|---|---|---|
| 1.1 `rate_limit.lua` | ✅ | ZSet 滑窗：`ZREMRANGEBYSCORE` 清过期成员 → `ZCARD` 计数 → 达阈值返 `0` → 放行 `ZADD`+`EXPIRE` 返 `1`；头注释写明键/参数契约与"滑窗 vs 固定窗口、member 唯一性、EXPIRE 刷新"的理解 |
| 1.2 RedisConfig Bean | ✅ | `rateLimitScript`（`RedisScript<Long>`）注册 |
| 1.3 常量 | ✅ | `RATE_LIMIT_PREFIX`（`rate:limit:`）/ `RATE_LIMIT_WINDOW_SECONDS`(60s) / `RATE_LIMIT_MAX_COUNT`(5次) |
| 1.4 execute 接入 | ✅ | 限流位于 execute **第 0 步**（早于活动查询/幂等），超限抛 `RATE_LIMITED(42900)`「请求过于频繁」 |
| 1.5 测试 | ✅ | `RateLimitTest` 4 用例全绿：脚本级放行/拒绝（前 5 次 `1`、第 6 次 `0`）、真实滑窗清理（历史 score 成员被清后放行）、service 级限流最先（**不存在的活动也先 `42900` 而非 `NOT_FOUND`**）、不同用户隔离 |

**Day 1 验收标准：** 单用户窗口内超阈值返回 `42900`、滑窗后恢复、不同用户隔离、全量 BUILD SUCCESS。—— ✅ 达成（2026-09-04，全量 `mvn test` 32 用例 BUILD SUCCESS）

**Day 1 经验教训：**

1. **限流闸门先于幂等，测试要认知链路顺序**：同用户高频请求会先被限流拦（`42900`）而非幂等拦（`40901`）；因此并发防重用例的并发度必须 ≤ 限流阈值，否则超额请求被限流"截胡"，`duplicateCount` 断言失败（`SeckillUserDedupTest.testConcurrentSameUser` 曾因此把 42900 当意外异常）。
2. **限流放行语义不能用"同用户多次 execute 成功"验证**：第 2 次起会被秒杀幂等拦成 `40901`。放行/拒绝应**脚本级直测** `rateLimitScript`；service 级用"预填满限流键 → `execute` 抛 `42900`"验证接入与执行顺序。
3. **滑窗"滑动"要真实**：用历史 score 成员 + 短窗口执行脚本，验证 `ZREMRANGEBYSCORE` 真的清掉过期成员后放行；"删键模拟滑动"只证明了"键空了能放行"，没测到滑窗清理逻辑。
4. **限流键跨测试类残留**：`execute` 每次写 `rate:limit:{userId}`（TTL 60s），凡调用 execute 的测试类 setUp/tearDown 都要清 `rate:limit:*`；本次 Dedup 因其它类残留成员，第二次请求被 `42900` 顶掉预期的 `40901`。
5. **ZSet member 必须唯一（时间戳 + 随机）**：同一秒内 member 相同会被 `ZADD` 覆盖导致计数丢失；放行时刷新 `EXPIRE`，活跃用户键不提前淘汰。

### 第 3 阶段 Day 2 进度 — Redis Stream 生产者削峰 ✅

| 任务 | 状态 | 说明 |
|---|---|---|
| 2.1 消息常量 + 消息体 | ✅ | `CacheKeyConstant` 增 `SECKILL_ORDER_STREAM`（`seckill:order:stream`）与 `SECKILL_DEAD_STREAM`（备用）；`SeckillOrderMessage`（activityId/productId/userId/orderNo/seckillPrice/requestTime） |
| 2.2 订单号生成 | ✅ | `OrderNoGenerator`：`SK` + `yyyyMMddHHmmssSSS` + 4 位序号，`synchronized` 线程安全，每毫秒 9999 上限 |
| 2.3 Stream 生产者 | ✅ | `stream/producer/SeckillOrderStreamProducer`：`opsForStream().add(StreamRecords.objectBacked(msg).withStreamKey(...))`，返回 `RecordId` |
| 2.4 execute 接入削峰 | ✅ | Lua `result>=0` → 生成 orderNo → 构建消息（`activityVO` 取 productId / 成交价快照防改价）→ XADD → 返回 `QUEUED + orderNo`；XADD 失败先抛系统异常，注明 Day 4 统一补偿 |
| 2.5 测试与回归 | ✅ | 新增 `SeckillPublishStreamTest` 3 用例（成功 orderNo + Stream 恰 1 条 / 重复秒杀不追加 / 售罄不产生消息）；Concurrency/Dedup/RateLimit 补 stream 键清理 |

**Day 2 验收标准：** execute 成功返回带 `orderNo` 的 `QUEUED` 且 Stream 可读回、失败路径不产生消息、全量 BUILD SUCCESS。—— ✅ 达成（2026-09-05，全量 `mvn test` 35 用例 BUILD SUCCESS）

**Day 2 经验教训：**

1. **新逻辑要放进对应分支，别写在"全分支终止"之后**：把 Stream 发送写在了 `if/else if/else`（每分支都 return/throw）之后 → 永远不可达的死代码；且误用 `activity`（应为 `activityVO`）、`scriptResult`（应为 `result`）变量。`mvn compile` 一次性抓出 3 个符号错误，写完必须编译验证。
2. **重构注意成对括号**：方法闭合 `}` 与新逻辑重叠会产生"提前闭合类"的多余大括号（`getActivityVO` 掉到类外报错）；替换后检查括号配对或看方法缩进。
3. **削峰顺序不可反**：必须先扣库存成功、再 XADD（消息 = 已拥有库存的凭证）；XADD 失败时库存已扣、令牌已建，本阶段抛系统异常、补偿留待 Day 4，不在本地自行做复杂回滚。
4. **成交价随消息快照下发**：消费者落库不再查活动表，杜绝"活动改价后金额不一致"。
5. **测试隔离"三件套"**：execute 成功会写 stream，凡调 execute 的测试类现在需清理 `rate:limit:*` + `seckill:user:*` + `seckill:order:stream` 三类键（Day 1 教训 4 的扩大版）。
6. **Redis host 端口转发当日 3 次断连**：容器内 PONG 正常、host 连接被 reset（`An established connection was aborted`）→ `docker restart my-redis` 每次可恢复；若继续高频出现，建议 `wsl --shutdown` 重建网络再启动容器，并排查端口/资源占用。

### 第 3 阶段 Day 3 进度 — 消费者异步落单 ✅

| 任务 | 状态 | 说明 |
|---|---|---|
| 3.1 常量 + Mapper | ✅ | `CacheKeyConstant` 增消费者组 `SECKILL_ORDER_GROUP`；`SeckillOrderMapper.selectByOrderNo` + XML 实现 |
| 3.2 消费者组件 | ✅ | `stream/consumer/SeckillOrderConsumer`：`ensureGroup()`（`XGROUP CREATE ... MKSTREAM`，BUSYGROUP 幂等）；`consumePending(count)` 用消费者组 XREADGROUP 拉取新消息 |
| 3.3 落库 + XACK | ✅ | `handle` 解析消息 → `selectByOrderNo` 查重 → 插入 `SeckillOrder`（成交价快照 / `status=CREATED` / streamMessageId）→ XACK；`DuplicateKeyException` 兜底视为已处理；失败不 ACK 留 PEL |
| 3.4 订单查询接口 | ✅ | `OrderController`：`GET /api/seckill/orders/{orderNo}`（interface 4.9），未落库返回 `QUEUING`，已落库返回 `CREATED` 及明细 |
| 3.5 集成测试 | ✅ | `SeckillConsumerIntegrationTest` 2 用例（真实 DB：插入未来活动 + 预热 + execute + 消费断言订单落库字段 / 重复消费不重复落库），tearDown 清理活动订单键 |
| 3.6 全量回归 | ✅ | 默认不启动自动消费（手动调 `consumePending`，避免后台线程干扰测试） |

**Day 3 验收标准：** execute 成功 → 消费者能转成订单且 XACK 不重复消费、DB 唯一键兜底不产生重复订单、查询接口可查 `CREATED`、全量 BUILD SUCCESS。—— ✅ 达成（2026-09-05，全量 `mvn test` 37 用例 BUILD SUCCESS）

**Day 3 经验教训：**

1. **BUSYGROUP 的判断要看 cause 链**：Spring Data Redis 把 lettuce 的 `RedisBusyException` 包装成外层 `RedisSystemException`（message 是笼统的 "Error in execution"），真实原因在 cause 里。只查 `e.getMessage()` 会漏判 → `@PostConstruct` 建组抛异常 → 全量测试中第一个 context 失败后触发 Spring 测试 **failure threshold=1**，后续所有新 context 的测试类全部"快速跳过"（表现为 0.001s 全 Error）——排查时先找首个 context 的根因，别被连锁失败误导。
2. **`StreamRecords.objectBacked(bean)` 的双重序列化坑**：bean 字段经序列化器编码后再入 Stream，消费者 XREADGROUP 读回 MapRecord 时字段值会变成 **Base64 字符串**（`200` → `"MjAw"`），`Long.valueOf` 直接抛 `NumberFormatException`。修正：生产者用**明文 Map** 投递（字段全为可读字符串），消费者侧用 `toLong/toStr` 安全转换，不依赖强转。
3. **Redis 容器重启即丢数据**：`my-redis` 无持久化配置，`docker restart` 后所有 key 清空 → 全量里首个 context 建组、后续 context 遇 BUSYGROUP。组创建必须幂等（MKSTREAM + cause 链判 BUSYGROUP）。
4. **消费者组消息可靠性机制**：XREADGROUP 读到的消息 ACK 前进入 PEL，进程崩溃可重读；`XACK` 才移除——这是"宕机重启消息不丢"的基础，也是 Day 4 重试/死信的前提。
5. **消费测试用真实 DB + 外键意识**：`seckill_order` 有 `fk_order_activity/product`，落库测试不能用 mock 活动，需插入真实活动（`product_id` 引用种子商品）并在 tearDown 清理，避免脏数据与跨用例污染。
6. **常量/类名先行**：`CONSUMER_NAME` 曾用 `System.getenv("HOSTNAME")`（Windows 无此变量拼出 `consumer-null-*`），统一改 UUID；命名/常量先定义清楚再实现。

### 第 3 阶段 Day 4 进度 — 消费可靠性（重试 / 死信 / 消息日志） ✅

| 任务 | 状态 | 说明 |
|---|---|---|
| 4.1 日志实体 + Mapper | ✅ | `SeckillMessageLog` 实体 + `SeckillMessageLogMapper` + `MessageLogStatusEnum`（PENDING/SUCCESS/FAILED/DEAD） |
| 4.2 常量 | ✅ | 死信流复用 `SECKILL_DEAD_STREAM`；新增 `MESSAGE_MAX_RETRY`（默认 3） |
| 4.3 消费接日志 | ✅ | `handle` 按 `stream_message_id` 查/建 PENDING 日志；成功置 SUCCESS；失败置 FAILED + retry+1 + error_message（截断 1023） |
| 4.4 重试 + 死信 | ✅ | 未达阈值**不 ACK 留 PEL**（可重试）；达阈值 XADD 死信流（明文 Map 带原字段+错误）→ 日志 DEAD → ACK 原消息 |
| 4.5 重试入口 | ✅ | `consumerRetry(count)`：`XREADGROUP ... STREAMS stream 0` 从 PEL 拾起未 ACK 消息（与 `consumePending` 的 `>` 互补） |
| 4.6 测试 + 回归 | ✅ | `SeckillMessageReliabilityTest` 2 用例：成功日志 SUCCESS+订单落库 / 手工投递坏消息（活动 999999 触发外键失败）→ retry 递增留 PEL → 达阈值转死信（日志 DEAD + 死信流 1 条） |

**Day 4 验收标准：** 成功/失败/死信三态日志流转正确；失败未超限可重试、超限进死信并 ACK；全量 BUILD SUCCESS。—— ✅ 达成（2026-09-06，全量 `mvn test` 39 用例 BUILD SUCCESS）

**Day 4 经验教训：**

1. **重构大方法时先画清楚 try/catch 归属**：`handle` 嵌套两层 try，外层缺 `catch/finally` 直接编译失败，且连累后续方法"需要 class/interface/enum"连环报错；业务分支还**丢失了订单 `insert`**——只 ACK 不落库是最危险的一类 bug，审查时必须核对"副作用语句是否还在"。
2. **`@TableField(fill = ...)` 必须配套 `MetaObjectHandler`**：`SeckillMessageLog.createdAt/updatedAt` 标了自动填充但项目没有处理器 → `INSERT` 显式带 NULL → MySQL strict 模式报 `Column 'created_at' cannot be null`。已新增 `MybatisPlusMetaObjectHandler`（只对带 fill 注解的字段生效）。
3. **死信/消息投递统一用明文 Map**：死信若沿用 `ObjectRecord.create` 又会踩 Day 3 的 Base64 序列化坑；字段全字符串化投递，消费者端 `toLong/toStr` 安全转换是通用约定。
4. **"失败不 ACK 留 PEL"就是重试机制**：`XREADGROUP 0` 读 PEL 重投；ACK 则失去重试机会；达阈值 XACK + 转死信让主 Stream 不积压。
5. **错误信息入库要截断**：`error_message` 是 VARCHAR(1024)，`e.getMessage()` 可能超长导致再次落库失败，入库前 `substring(0, 1023)` 兜底。

### 第 3 阶段 Day 5 进度 — 分布式锁落地 + 阶段验收 ✅

| 任务 | 状态 | 说明 |
|---|---|---|
| 5.1 锁常量 | ✅ | `CacheKeyConstant` 增 `SECKILL_LOCK_PREFIX`（`seckill:lock:`，预热锁键 `seckill:lock:preheat:{activityId}`） |
| 5.2 预热加锁 | ✅ | `preheatActivity` 用 Redisson `tryLock(wait=5s, lease=30s)` + **锁内双重检查**（重新查库防等锁期间已被预热）→ `doPreheat` → `finally` + `isHeldByCurrentThread` 释放、中断恢复位；注释写明"预热需锁 / execute 不需锁（Lua 已原子）"取舍 |
| 5.3 库存重置 | ✅ | `resetStock`：DB 配置库存刷回 `seckill:stock:` 并刷新 TTL，同加锁防并发（运维 / 压测前重置） |
| 5.4 并发预热测试 | ✅ | `PreheatConcurrencyTest`：8 线程并发预热恰 1 成功 + 7 个「不要重复」拒绝 + `update` 恰 1 次 + 活动/库存缓存存在 + 锁键释放 |
| 5.5 阶段验收 | ✅ | 全量 `mvn test` 40 用例 BUILD SUCCESS（2026-09-06）；本表 + 第 3 阶段整体验收小结如下 |

**✅ 第 3 阶段整体验收小结（2026-09-06）：**

- **链路闭环达成**：`execute` 限流（ZSet 滑窗 Lua）→ Lua 原子（幂等令牌 + 扣减 + 售罄回滚）→ Stream 削峰（明文 Map 发布 + orderNo）→ 消费者组异步落单（DB 唯一键兜底 + XACK）→ 消费可靠性（PEL 重试 / 死信流 / `seckill_message_log`）→ 分布式锁守护预热/库存重置等写入口；
- **能力实证（40 用例）**：防超卖（200 并发成功恰 100）、防重（同用户并发仅 1 成功）、限流（滑窗放行/拒绝/真实滑动）、Stream（发布/消费/XACK/幂等）、可靠性（成功/失败重试/死信三态）、预热并发（1 成功 + update 1 次 + 锁释放）；
- **Redis 键收敛**：`rate:limit:` / `seckill:user:` / `seckill:order:stream`+`group` / `seckill:order:dead:stream` / `seckill:lock:` / 消息追踪表 `seckill_message_log`；
- **生产注意**：`my-redis` 无持久化、`docker restart` 即丢数据（期间多次踩坑，正式部署应开启 RDB/AOF）；Windows→WSL host 端口转发偶断需 restart 恢复。

**Day 5 经验教训：**

1. **分布式锁与 Lua 原子的分工**：预热是"查状态→写缓存→更新 DB"的多步读改写，必须锁串行化；execute 的单键原子（防重+扣减）已由 Lua 完成，主链加锁反而降并发。选型先看操作是否"单命令可原子"。
2. **锁内必须双重检查**：等锁期间别人可能已完成，拿到锁后要重新读库状态，不能信任拿锁前读到的旧快照。
3. **`tryLock(wait, lease)` + `finally` 释放**：wait 防无限等待、lease 防持锁崩溃死锁；释放前 `isHeldByCurrentThread()` 判断，防止误释放他人锁。
4. **`InterruptedException` 要恢复中断位**：`Thread.currentThread().interrupt()` 后再抛业务异常，别吞中断。
5. **大文件编辑易结构错乱**：本次曾出现无关 import（Redisson/kafka 误导入）、`doPreheat` 重复定义、`resetStock` 跑到类外、残缺语句（`long ttl = ...; } + 常量;`）——保存前用 `mvn compile` 验证。
6. **测试匹配要对着真实文案**：识别"已被预热"应匹配服务端真实消息（含「不要重复」），不能自造「活动已预热」子串，否则被拒线程全部落入意外异常。

### 第 4 阶段开发计划（排行榜 / 运行指标 / 前端演示 / 全链路压测）

> 阶段目标：把"能扛高并发"升级为"看得见、可演示、可量化"——订单成功上实时榜（ZSet），活动运行指标可查（计数键 + `seckill_activity_snapshot` 快照），浏览器一键完成秒杀演示闭环（前端页 + 订单轮询 + 榜单轮询），最后用 JMeter 全链路压测出具报告（补第 3 阶段 3.2 延后项）。

- **现有基础（第 3 阶段遗产）**：`execute` 全链路（限流 → Lua 原子 → Stream 发布）✅、消费者异步落单 + 幂等 + XACK（`seckill_order` 唯一键兜底）✅、`seckill_activity_snapshot` 表已建（schema.sql 5 表之一，尚未使用）✅、契约已定（database.md：`seckill:rank:{activityId}`；interface.md 4.11 `/api/rank/top10`、4.12 `/api/admin/.../metrics`）✅、`static/` 与 `templates/` 空目录待用 ✅。
- **本轮缺口**：榜单 ZSet 写入与 topN 接口不存在；指标**计数键**不存在（execute 三处拒绝分支未埋点 INCR）、快照采集与 metrics 接口不存在；前端零页面；`scripts/jmeter/` 未建；早期遗留文档引用（`docs/day.md` 系旧称、从未入库，分日计划已并入本 plan.md）尚待统一。

| 天 | 主题 | 状态 | 核心产出 |
|---|---|---|---|
| Day 1 | 实时秒杀成功榜 | ✅ | 消费者成功落单即上榜（按活动隔离）；`GET /api/rank/top10?activityId=` 返回 Top10 |
| Day 2 | 活动运行指标 | ✅ | execute 拒绝/成功埋点计数键 + 快照落库 + `GET /api/admin/seckill/activities/{id}/metrics` |
| Day 3 | 前端秒杀看板（AI 执行） | ✅ | 独立 Vue3+Vite 工程 `frontend/`：秒杀大厅（倒计时/状态机/订单轮询）+ 管理控制台 + 实时手速榜 + 运行指标面板 |
| Day 4 | 联调与可演示闭环（AI 执行） | ✅ | 自动消费调度 + check 口径统一 + 端到端验证：建单 CREATED / 榜单刷新 / check=ALLOW |
| Day 5 | 全链路 JMeter 压测 | ✅ | `scripts/jmeter/` + 5000 并发（多 userId 绕单用户限流）+ 压测报告（库存精确性/限流拒绝/端到端延迟/Redis 指标） |
| Day 6 | 复盘与总结 | ✅ | 异常与边界补充（E2~E6 已收尾）；学习收口三件套：`docs/redis-summary.md`《Redis 实战总结》+ `docs/notework.md` 学习笔记沉淀 + plan.md 收尾；阶段验收与提交 |

**阶段验收标准：**
1. **可演示闭环**：浏览器一键完成「秒杀 → 排队 → 轮询订单 → CREATED → 排行榜刷新」，按钮状态随活动窗口/库存/令牌实时正确；
2. **压测报告**：5000 并发下库存精确归 0、DB 成功订单数不超库存、限流按预期拒绝、端到端延迟与 Redis 资源可量化；
3. **学习收口**：六类 Redis 企业问题（缓存/原子/分布式锁/限流/消息/排行）各有代码位置与笔记对应，文档/进度/代码同步收尾。

### 第 4 阶段 Day 1 进度 — 实时秒杀成功榜 ✅（2026-09-07）

| 任务 | 状态 | 说明 |
|---|---|---|
| 1.1 常量 | ✅ | `CacheKeyConstant` 增 `SECKILL_RANK_PREFIX`（`seckill:rank:`，完整键 `seckill:rank:{activityId}`）+ `RANK_DEFAULT_TOP`(10) / `RANK_MAX_TOP`(100) |
| 1.2 榜单服务 | ✅ | `SeckillRankService` + Impl：`recordSuccess` 用 **ZADD NX**（`addIfAbsent`），score=**抢单成功时刻**（execute 侧 `requestTime`，越早越靠前）；`topN` 用 `ZRANGE` 升序 + `IN(userIds)` 批量回查订单回填 `orderNo`；入榜失败仅 warn 不外抛 |
| 1.3 消费者埋点 | ✅ | `handle` 订单"最终成功"路径统一 `recordSuccess`（首次 insert / 幂等命中 / `DuplicateKey` 兜底 / SUCCESS 终态补录），NX 幂等不重复计分，不阻塞 ACK |
| 1.4 排行榜接口 | ✅ | `RankController`：`GET /api/rank/top10?activityId=`（`@NotNull` 必填，message「活动ID不能为空」）；`top` 可选、默认 10、上限 100 |
| 1.5 集成测试 | ✅ | `SeckillRankTest` 5 用例全绿：全链先后序（score 严格递增）/ 幂等重放不重分 / 防重回归榜不变 / 10 用户并发恰好 10 人无重复 / 空榜返回空 |

**Day 1 验收标准：** 成功订单实时入榜且与 DB 先后一致；重复与重放不重复计分；TOP-N 接口契约对齐 interface 4.11。—— ✅ 达成（2026-09-07，全量 `mvn test` 45 用例 BUILD SUCCESS）

**Day 1 经验教训：**

1. **改 `handle` 结构别把整块判断挪到 `return` 之后**：本想把"SUCCESS 终态补录榜单"复制进 `DuplicateKey` 分支，结果终态块落在 `return;` 后成为**不可达语句**（编译直接失败），且 `DuplicateKey` 分支被展开成裸 `updateById` 绕过了 `markSuccess`。导师代修：终态块还原到原位置，三条成功路径统一走"`recordSuccess`（NX 幂等）→ `markSuccess` → `ack`"。
2. **榜单 score 不能取"消费者处理时刻"**：同一消费批次的多条消息会落在同一毫秒入榜，把 execute 的先后序彻底打乱（测试 sleep 拉开的是抢单时刻，断言 score 严格递增必 flaky）。正确 score = **消息里携带的业务时间戳**——本系统 `SeckillOrderMessage.requestTime` 早在第 2 阶段就随消息下发，直接复用即可。这印证了"消息体带业务发生时间"的价值。
3. **ZADD NX 天然幂等，化解"落库与上榜不同事务"的恰好一次难题**：member=userId 已存在时 `addIfAbsent` 不覆盖不重复；PEL 重放 / 幂等命中 / 终态补录反复触发都不脏榜。若用 `ZINCRBY` 计数则必须严格只在"首次 insert"调用，重放即重复加分。
4. **代码审查注意点**：测试类出现误 import（`org.w3c.dom.stylesheets.LinkStyle`）与同类型字段重复 `@Autowired`（`rankService`/`seckillRankService` 注入两遍）；组装 VO 时声明了 `orderNo` 局部变量却忘了塞进 builder（接口契约字段为 null）——IDE 自动补全与"声明未使用"都是审查信号。
5. **榜单与订单的先后关系**：`topN` 回查订单用 `orderByAsc(created_at)` 保证与榜单序一致；`toMap` 前可安全假设同活动同用户唯一单（DB `uk_activity_user` 兜底）。

### 第 4 阶段 Day 2 进度 — 活动运行指标 ✅（2026-09-07）

| 任务 | 状态 | 说明 |
|---|---|---|
| 2.1 常量 | ✅ | `CacheKeyConstant` 增 `SECKILL_METRIC_PREFIX`（Hash 键 `seckill:metric:{activityId}`）+ 字段常量 `rateLimitReject` / `duplicateReject` / `soldOutReject` |
| 2.2 execute 拒绝埋点 | ✅ | 三类拒绝在抛异常前 `HINCRBY 1`（限流 42900 → rateLimit / Lua -2 → duplicate / Lua -1 → soldOut）；私有 `incrementMetric` try/catch 容错仅 warn，不阻断主链 |
| 2.3 MetricsService | ✅ | `collectMetrics` 实时聚合（库存按 Number 转 int / 订单数与成功数同取 DB 口径统一 / 积压=XLEN−订单数近似在途并注释局限 / Hash 计数 HMGET）；`captureSnapshot` 快照落库 `seckill_activity_snapshot` |
| 2.4 管理端接口 | ✅ | `AdminMetricsController`：`GET /api/admin/seckill/activities/{activityId}/metrics`（对齐 4.12）+ `POST .../snapshot` 手动打点（压测/复盘用） |
| 2.5 实体/Mapper | ✅ | `SeckillActivitySnapshot` + `SeckillActivitySnapshotMapper`（BaseMapper） |
| 2.6 集成测试 | ✅ | `SeckillMetricsTest` 6 用例全绿：售罄 / 重复 / 限流（1 成功+4 重复后第 6 次限流）/ 聚合一致性（含 XLEN=2 而积压=0 口径验证）/ 快照落库 / 空活动兜底 |

**Day 2 验收标准：** execute 三类拒绝实时计数与 DB 核对一致；metrics 接口字段与 Redis/DB 一致；快照落库可用。—— ✅ 达成（2026-09-07，全量 `mvn test` 51 用例 BUILD SUCCESS）

**Day 2 经验教训：**

1. **`RedisCallback` 里"调用 `serialize` 却丢了返回值、再去引用未定义的变量"**：`redisTemplate.execute((RedisCallback<Long>) conn -> { getStringSerializer().serialize(key); return conn.xLen(keyBytes); })` ——序列化结果必须接住，`keyBytes` 要先定义后引用（典型编译错，已修）。
2. **限流埋点字段误填 `METRIC_FIELD_DUPLICATE`**：邻近分支常量复制粘贴是埋点 bug 高发点。写完按"抛出的 `ResultCode` ↔ 埋点字段"逐条核对（42900↔rateLimit / -2↔duplicate / -1↔soldOut）。
3. **无用/重复注入要审查**：误重复注入 `streamProducer`（与已有字段同类型）、注入 `rankService` 但 execute 全程未用（榜单在订单落库的消费者侧记，execute 抢单成功≠落库成功）——`@RequiredArgsConstructor` 不报错，但不代表依赖合理。
4. **Stream"积压"不能直接取 `XLEN`**：消息 XACK 后不会 XDEL，`XLEN` 恒等于历史总投递量。用 `XLEN − 该活动已落库订单数` 近似"已入队未落库的在途消息"，注释其"多活动共享单键会低估他活动积压"的局限。
5. **`limit_per_user` 不是防重开关**：系统按"每人一单"令牌语义防重（第 2 次即 `DUPLICATE`），原用例"限购 10=同用户连抢 5 次成功"的假设不成立。触发限流靠第 0 步对每请求计数：1 成功 + 4 重复后第 6 次被 `RATE_LIMITED`。
6. **测试方法缺闭合大括号把后续 `@Test` 吞进方法体**（Day 2/4 教训重演）：本日 3 个用例嵌套错乱致编译失败，重写文件为 6 个平级方法。写完用 IDE 折叠或格式化让结构错误显形。
7. **误 import 与本需求无关的类**（Redisson `BucketSetOperation`、Spring `ReactiveSetOperations`）：自动导入要挑包，不是"能补全"就正确。

### 第 4 阶段 Day 3/4 进度 — 前端看板 + 联调闭环 ✅（2026-09-07，AI 执行）

> panda 指定：前端部分全部由导师执行，使用 Vue 3 搭建独立工程 `frontend/`（不再用 Thymeleaf 方案）；参考仓库 `shopping_mall`（经探查为空目录，无复用价值）、`ui-ux-pro-max-skill-main`（取其语义色 token / tabular 数字 / 状态反馈设计规范）、`frontend-slides-main`（仅作视觉灵感，非组件库）。

| 任务 | 状态 | 说明 |
|---|---|---|
| 3.1 前端工程 | ✅ | `frontend/`：Vite 5 + Vue 3.4（script setup）+ Vue Router 4；`/api` dev proxy → `:8081` 免跨域；语义色 CSS token（CTA 橙 `#ea580c`、成功绿、破坏红），倒计时/价格/榜单数字统一 tabular-nums |
| 3.2 秒杀大厅 `/` | ✅ | 活动定位条（输入 id / 引导创建）→ 活动+商品卡 → **倒计时状态机**（开抢前禁购、进行中解锁、售罄/结束/取消拦截）→ 一键秒杀 → 排队轮询订单（900ms×60）→ 成功后榜单即时刷新；40901 自动去榜单同步自己已入队订单；未预热提供"立即预热" |
| 3.3 管理控制台 `/admin` | ✅ | 新建演示活动（秒后开始/时长/价格元→分/库存/限购）→ 自动预热 → 跳转大厅；既有活动预热工具；演示小贴士 |
| 3.4 排行榜 / 指标面板 | ✅ | `RankBoard`（3s 轮询，前三奖牌高亮 + stagger 入场，score 毫秒展示为"抢到时间"）；`MetricsPanel`（4s 轮询：库存/成功订单/队列积压/三类拒绝 + 手动打点快照按钮） |
| 4.1 自动消费调度 | ✅ | **联调关键补丁**：原系统无自动消费，execute 入队后无人建单（订单恒 QUEUING、榜单不更新）。新增 `StreamConsumerScheduler`（`@EnableScheduling`，新消息 150ms / PEL 重试 5s），开关 `flash.stream.auto-poll`（main yaml=true；15 个集成测试统一加 `@SpringBootTest(properties="flash.stream.auto-poll=false")` 隔离，避免后台轮询与用例手动驱动竞态） |
| 4.2 check 口径统一 | ✅ | `checkActivity` 原按缓存 status 快照（NOT_STARTED）判不可参与，与 execute 的时间窗动态口径矛盾（UI 显示"未开始"却可抢通）；重构为与 execute 一致：CANCELLED 拦截 → 实时时间窗 → 实时库存；`Phase1IntegrationTest.testCheckActivityEnded` 同步语义化（ENDED 需 endTime 已过） |
| 4.3 端到端联调验证 | ✅ | 脚本化全链路：创建活动→预热→check=ALLOW→双用户 execute→自动消费→订单 CREATED→榜单 2 人且先抢者居首→metrics 成功数一致；Vite 代理 200；浏览器预览 `http://localhost:5173` 可完整演示 |

**Day 3/4 验收标准：** 浏览器一键完成「创建并预热 → 倒计时开抢 → 秒杀 → 排队 → 订单 CREATED → 排行榜刷新 → 指标实时」，按钮状态随窗口/库存/令牌正确。—— ✅ 达成（2026-09-07，全量 `mvn test` 51 用例 BUILD SUCCESS + 端到端脚本验证）

**Day 3/4 经验教训（联调暴露的系统级问题）：**

1. **"入队成功"≠"订单落库"——削峰必须有自动消费调度**：此前 Stream 消费者只在集成测试被手动拉起，浏览器/JMeter 场景下消息无人消费，订单恒 QUEUING。调度器必须与测试隔离：用 `@ConditionalOnProperty` 开关 + 测试注解显式关闭；曾尝试 Windows `cmd set 环境变量` 方式传开关不可靠（Spring 环境变量 relaxed binding 未按预期生效），最终落地"配置常开 + 测试 properties 覆盖"。
2. **check 与 execute 的"活动是否开放"口径必须同源**：check 若用预热缓存里的 status 快照、execute 用实时时间窗，前端就会"提示未开始却能抢成功"。统一规则：`CANCELLED 显式拦截 → 实时时间窗（未开始/已结束）→ 实时库存（售罄）`，status 仅辅助。
3. **前端契约以真实 Controller/VO 为准，而非 interface.md 理想值**：联调前逐个核对（Result 结构 / 分与元 / `QUEUED`/`QUEUING`/`CREATED` 枚举 / metrics 字段 / 活动详情字段），避免按文档理想写死。
4. **全局共享 Stream 键使"队列积压=XLEN−本活动订单数"在多活动并存时失真**：该口径对单活动演示/压测准确，界面与实现注释均明确局限，多活动看板需 PEL/单活动流方向优化（Day 5 压测前如需可再议）。
5. **Vite dev proxy 免 CORS**：前后端分离（5173 → 8081 `/api` 代理），无需后端额外 CORS 配置。
6. **UI 数字抖动**：倒计时/价格/榜单用 `font-variant-numeric: tabular-nums`；榜单前三用"数字徽章+颜色"双重标注（不只靠色）；动效 ≤300ms 且 `prefers-reduced-motion` 降级。

**运行方式：** 后端 `mvn spring-boot:run`（:8081）→ 前端 `cd frontend && npm install && npm run dev`（:5173）→ 浏览器开 http://localhost:5173（详见 `frontend/README.md`）。

### 第 4 阶段增强①：图片存储方案 — 本地磁盘 / 阿里云 OSS 双策略 ✅（2026-09-07）

- **抽象**：`ImageStorageService`（策略接口）+ `AbstractImageStorage`（扩展名白名单、文件头校验 / 5MB 上限 / UUID 安全命名，防脚本文件与路径穿越）+ `LocalImageStorageService` / `OssImageStorageService` 双实现，`@ConditionalOnProperty(aliyun.oss.enabled=true|false)` 同一时刻仅一个 bean 生效。
- **接入**：`POST /api/admin/files/image`（multipart）→ 返回 `{url, storageType}`；`FileStorageWebConfig` 把 `/uploads/**` 映射到本地目录（磁盘读取）；返回 URL 已按当前请求主机拼接（本地上传实测回显 200，非图片格式拒绝 `40001`）。
- **商品回填闭环**：前端管理控制台新增「商品主图管理」（载入商品 → 上传图片 → 保存商品 image_url → 清缓存），活动广场卡片与详情页即时展示（`ActivityItemVO.productImage` / `ProductVO.imageUrl` 链路已通）。
- **OSS 策略**：凭据全部**配置化**（`application.yaml` 的 `aliyun.oss.*`：`enabled` 开关 + `endpoint`/`bucket-name`/`domain` + 密钥），密钥支持 `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` 环境变量覆盖，不落 Git；默认走本地磁盘，设置 `aliyun.oss.enabled=true` 后才激活 OSS（bucket `panda-tea` / 北京地域）；缺密钥时上传返回可读错误且不发网络请求。SDK 依赖 `aliyun-sdk-oss` 已加入 pom。接入/切换指引见 `docs/storage.md`。
- 全量 `mvn test` 51 用例 BUILD SUCCESS。

### 第 4 阶段 Day 5 — JMeter 全链路压测 ✅（2026-09-08）

| 任务 | 状态 | 说明 |
|---|---|---|
| 压测脚本 | ✅ | `scripts/jmeter/`：`seckill_main.jmx`（5000 唯一用户 execute 主压，可 `-J` 参数化）、`seckill_load.jmx`（预埋+主压+重复/限流分段版）、`run_load.ps1`、README |
| 执行与结果 | ✅ | 5000 请求 ramp90s：**0 错误**，吞吐 55.5/s，avg 24ms / p50 17 / p90 23 / p99 510ms；后端守恒 order=1000=库存、soldOutReject=4000、redisStock=0（无超卖） |
| 压测暴露 Bug 修复 | ✅ | **`OrderNoGenerator` 双时钟源毫秒错位 → 订单号并发碰撞**（uk_order_no 幂等兜底吞单："库存 0 订单 999"）；修复为单一时钟源 + 单毫秒超限让位；复测日志 1000/1000 无重复、订单=库存 |
| 报告 | ✅ | `docs/load-test-report.md`（含修复前后对比、防超卖/削峰/幂等结论与调优建议） |

**Day 5 验收：** 5000 并发下库存精确归零、成功订单数=库存、拒绝分布守恒、延迟与资源可量化。—— ✅ 达成（2026-09-08）
**Day 5 经验教训：** ① 唯一 ID 的"时钟源一致性"是并发唯一性暗坑（判定时钟与输出文本必须同一来源）；② 压测客户端同机短 ramp 会因端口/TIME_WAIT 产生连接失败，需区分"客户端注入失败"与"系统错误"；③ 业务拒绝（HTTP200+code≠0）与 HTTP 层错误要分开统计，权威口径取后端计数；④ JMeter UDV 是静态值，参数化必须走 `${__P(...)}` 属性，`-J` 才能覆盖。

### 第 4 阶段增强②：AI 智能客服（OpenAI 兼容大模型）✅（2026-09-08）

- **后端**：`POST /api/support/chat`（`AiSupportController`）→ `AiChatServiceImpl` 将「系统人设 + **实时商品/秒杀目录**（system prompt 注入：在售商品 + 进行中/未开始活动，无向量库的教学取舍，注释写明）+ 前端带回最近 10 条对话历史（角色归一化/长度护栏）」组装为 OpenAI messages → `OpenAiCompatibleChatClient`（Spring `RestClient`，POST `{base-url}/chat/completions`，兼容百炼兼容模式 / DeepSeek / OpenAI）；回复纯文本透传（`ChatReplyVO.reply`）。
- **配置**：`ai.llm.base-url / api-key / model / temperature / max-tokens / timeout-seconds`（application.yaml；api-key 用 `${AI_LLM_API_KEY:}` 支持环境变量注入）；密钥或地址缺失时返回 **50300** 可读提示，绝不用空配置外呼；调用失败（网络/超时/非 2xx/结构异常）返回 **50301**。
- **前端**：`/ai-service` AI 客服页由纯 mock 改为真实对话（`api.aiChat` 携带多轮历史；加载失败以错误气泡展示后端可读提示）；侧栏「热门活动」替换原假"热门商品"，来源后端活动列表、可点击跳详情；「新建对话 / 清空」重置会话；LLM 回复按纯文本气泡渲染。
- **测试**：`AiSupportChatIntegrationTest` 4 用例（消息序列与 system 目录断言 / 15 轮历史截最近 10 + 角色归一 + 未知角色过滤 / 空问题 40001 / 超长 40001）；`AiClientUnconfiguredTest` 2 用例（缺 key / 缺 base-url 在发网络前抛 50300，客户端为懒初始化便于纯单测）；`OssStoragePolicyIntegrationTest` 2 用例。
- **OSS 配置化**：密钥硬编码 → `application.yaml aliyun.oss.*`（`enabled` + `endpoint/bucket-name/domain`）+ 环境变量覆盖（见增强①更新与 `docs/storage.md`）。
- 全量 `mvn test` **59 用例 BUILD SUCCESS**；前端 `npm run build` 通过。

### 第 4 阶段复盘前置：全量代码审计与整改 ✅（2026-09-08，整改已 git 提交 `49a0b09`，E 类待议项已拆卡见小节末）

> 审计报告见 `docs/audit-report.md`（高 R1~R5 / 中高 C1~C6 / 中 E1~E6，全量精读仅出清单未改码）。R/C/E 已按报告落地并 **git 提交（`49a0b09`）**，经回归（**62 用例 BUILD SUCCESS**）验证；整改期间出现过一轮回归修复（见下"回归教训"）。E 类剩余项已拆卡立项（见本小节末），进入 Day 6 收尾。

| 编号 | 问题摘要 | 整改状态 | 落地要点 |
|---|---|---|---|
| R1 | `IdGenerator` 超长 ID 破坏前端全链路（超 JS 安全整数） | ✅ 已改 | 后端 ID 相关字段处理 + 前端去掉 `Number()` 回环、全程字符串透传（`store.js`/`ActivityDetailView.vue` 等） |
| R2 | execute 发消息失败不回滚库存与令牌 | ✅ 已改 | 失败反向补偿（回补库存 + 删令牌）后重抛，与消费者侧补偿口径一致 |
| R3 | 无鉴权 + 密钥明文 | ✅ 已改 | 新增 `ApiAccessInterceptor`/`ApiAccessWebConfig`/`SecurityProperties`：`/api/admin/**` 走 admin 令牌、`execute`/`check`/`users/*/orders` 走用户令牌；密钥改环境变量（`AI_LLM_API_KEY`/`OSS_*`） |
| R4 | 活动"更新"不清预热缓存 | ✅ 已改 | update 成功后失效活动/库存缓存并重置预热态（联动 C5） |
| R5 | 毒消息永留 PEL + 死信无回放/无补偿 | ✅ 已改 | 解析异常也走统一失败计数（超阈值转死信）；进死信且订单未落库时补偿库存与令牌；新增 `DeadLetterReplayService` 人工回放（死信流快照 → 重新原子占位 → 重投主 Stream → XDEL），管理接口 `POST /api/admin/seckill/dead-letters/replay` |
| C1/C2 | orders 查询状态折叠、字段名不符契约 | ✅ 已改 | 按 `OrderStatusEnum` 输出 `status`/`statusDesc`，键名对齐 `createdAt`（interface 4.9） |
| C3 | interface 4.10 未实现 | ✅ 已改 | 新增 `GET /api/seckill/users/{userId}/orders?activityId=`（校验访问令牌匹配） |
| C4 | 全局异常缺参数/类型/404 处理 | ✅ 已改 | `GlobalExceptionHandler` 补参数缺失/类型不匹配/404 等 handler |
| C5 | check 未预热降级 DB 库存放行，口径与 execute 不一致 | ✅ 已改 | DB 回源分支细化：取消→`ACTIVITY_CANCELLED`、未开始→`ACTIVITY_NOT_STARTED`、已结束→`ACTIVITY_ENDED`，仅"可参与时间窗内未预热"→`ACTIVITY_NOT_PREHEATED`（不放行，与 execute 拒绝口径一致） |
| C6 | execute 失败响应 `data=null` 与契约不符 | ✅ 已改 | 失败路径包装统一响应结构 |
| E1 | `resetStock` 死代码 + `decrStockScript` Bean 无引用 | ✅ 已闭环 | Bean 恢复（回归 4 Error 修复）；脚本保留为验收资产；`resetStock` 语义接入列为可选项 E1-r（见下方立项卡，原则：勿在活动进行中复活库存） |
| E2~E6 | limitPerUser 无效 / 创建缺业务校验 / 限流粒度 / 文案 / 上传校验 | 🔄 已立项 | 已拆为 E2~E6 逐项卡片（见本小节末立项卡），按 E3→E4→E6→E5→E2 顺序收尾；仅 E5 的 SeckillCacheService 文案已于整改期同步收敛 |

**回归教训（本日 4 Failures + 4 Errors → 62 全绿）：**
① `RedisConfig` 删 Bean 会连锁打挂依赖旧 Bean 的既有验收测试——删除前先全局搜引用与测试注入（本次恢复 `decrStockScript` 解决 LuaStockDeductionTest 4 Error）；
② 集成测试 `setUp`/`tearDown` 必须清理**所有**用例会写入的 DB 行（含"坏消息"用的 id=999999），否则 replay 残留活动让外键不再失败 → 坏消息消费成功，用例互相污染（Reliability 测试补 `deleteById(BAD_ACTIVITY_ID)` 解决）；
③ 语义演进必须同步测试前置条件：check 改为"未预热不放行"后，`testCheckActivityRunning` 需先真实预热再断言 ALLOW（execute 本就只放行已预热活动）；
④ **跑全量测试必须先停 IDEA 里的 dev 后端**（`flash.stream.auto-poll=true` 自动消费会抢走集成测试写入 Redis Stream 的消息）：异步类用例（消费幂等、排行榜）随机少 1 条而"偶发失败"，实为 dev 与测试两进程抢消费，非代码 flaky——回归验证两次误判，停 dev 后全量稳定全绿。

> 用例数说明：当前全量 **73** = 增强② 59 + 死信回放/存储策略整改 +3（62）+ E3 业务校验 +10（72）+ E2 收尾注解夹逼与更新分支用例 +1（73）。

**E 类待议项立项卡（✅ 已收尾：2026-09-08 立项并于当日全部落地）**

> E 类（中）不阻塞现有演示闭环。下表任务已全部落地并过全量 mvn test（**73 用例 BUILD SUCCESS**，E3 校验测试 +10、E2 注解夹逼与更新分支用例 +1）；
> E4 双维粒度与 E6 魔数/固定 Content-Type 经复核在整改期已吸收，本次以"复核 + 限流参数 yaml 化 + 残余文案收敛"补齐收口。

| 任务 | 问题现状 | 建议方向（导师建议，可再议） | 验收口径 | 状态 |
|---|---|---|---|---|
| E3 · 创建/更新业务校验 | 活动/商品创建缺校验：`endTime`≤`startTime`、价格为负、库存 ≤0、productId 不存在（FK 异常落 50000）均可入 | `ActivityRequest`/商品 DTO 增加校验（时间序、价格非负、库存 >0、productId 存在性预查），失败走统一业务码，不冒 50000 | 非法入参被业务码拦截（非 50000 脏异常）；补对应测试用例 | ✅ 已落地（CreateBusinessValidationTest +10，全绿） |
| E4 · 限流粒度细化 | 限流仅 userId（60s/5 次）：多活动连点互相误伤、换 userId 可绕过 | 限流键升级 `userId:activityId` 双维（保留总量约束），窗口/阈值参数化进 yaml（默认 60s/5 保持现状） | 同用户跨活动不再互伤；单活动限流语义不破坏现有限流测试 | ✅ 已落地（粒度整改期已吸收；窗口/阈值 yaml 化 `flash.rate-limit.*`） |
| E6 · 上传校验加固 | 上传仅验扩展名/大小，Content-Type 全信客户端（伪造 MIME 可藏脚本文件） | 在现有文件头白名单基础上以魔数探测为准；响应 Content-Type 由服务端按扩展名固定，不反射客户端值 | 伪造 Content-Type 文件被拒；上传响应类型服务端可控；补测试 | ✅ 核心已落地（文件头魔数校验 + 服务端固定 Content-Type）；「文件内容与图片格式不匹配」合法图片误拒复现仍挂起待排查 |
| E5 · 异常文案收敛（残余） | OSS 上传失败把 `e.getMessage()` 原样回用户（含 SDK 内部细节） | 用户侧固定可读文案 + `log.error` 记录完整原因，错误码区分场景 | 失败响应不再暴露 SDK 堆栈/服务器细节，日志有完整根因 | ✅ 已落地（复核无回显点；预热失败文案收敛为固定提示） |
| E2 · limitPerUser 语义取舍 | 创建参数 `limitPerUser` 仅透传不生效；DB `uk_activity_user` 硬性一人一单（配置 >1 也不允许多买），误导使用者 | 秒杀"一人一单"是合理约束 → 建议移除该参数或创建时强制 =1 并注释说明受 DB 唯一键约束；若确需"每人 N 件"则要改表去唯一键 + Redis 计数（工程量高，教学不建议） | 参数语义与 DB 唯一键一致，无"配了不生效"的误导；文档/注释同步 | ✅ 已落地（DTO 加 @Min/@Max 夹逼=1 作 Controller 第一道闸，Service 创建/更新同入口兜底拒绝；前端 disabled+max=1+提示；测试补更新分支用例，全量 73 用例 BUILD SUCCESS） |
| E1-r | `resetStock` 已接入管理端接口与活跃期守卫 | 落地：`POST /api/admin/seckill/activities/{id}/reset-stock`（Redisson 锁 + 活动进行中拒绝重置），语义见 `SeckillCacheService#resetStock` | 仅未开始/已结束可重置；进行中重置返回业务错误 | ✅ 已落地（2026-09-09 复核） |

**认领顺序建议：** E3 → E4 → E6 → E5 → E2（取舍类放最后）；E1-r 可并入 E1 收尾或直接做"删除"取舍。任务由 panda 认领实现，导师审查。

---

## 四、数据库表结构参考

### 当前阶段使用的表

| 表名 | 实体类 | Mapper |
|------|--------|--------|
| `product` | `Product.java` | `ProductMapper.java` |
| `seckill_activity` | `SeckillActivity.java` | `SeckillActivityMapper.java` |
| `seckill_order` | `SeckillOrder.java` | `SeckillOrderMapper.java` |
| `seckill_message_log` | `SeckillMessageLog.java` | `SeckillMessageLogMapper.java` |
| `seckill_activity_snapshot` | `SeckillActivitySnapshot.java` | `SeckillActivitySnapshotMapper.java` |

### 后续阶段使用的表

（暂无——5 张表已全部投入使用）

---

## 五、Redis 缓存键设计（当前阶段）

| 缓存键 | 类型 | 用途 | 引入阶段 |
|--------|------|------|----------|
| `product:detail:{productId}` | String | 商品详情缓存旁路 | Day 4 ✅ |
| `seckill:activity:{activityId}` | Hash | 活动信息+时间窗口 | Day 6 |
| `seckill:stock:{activityId}` | String | 秒杀实时库存 | Day 6 |
| `seckill:user:{activityId}:{userId}` | String | 用户秒杀幂等令牌 | 第2阶段 Day 4 ✅ |
| `rate:limit:{activityId}:{userId}` | ZSet | 滑动窗口限流（活动×用户双维，E4 双维化） | 第3阶段 Day 1 ✅ |
| `seckill:order:stream` | Stream | 秒杀下单消息（削峰） | 第3阶段 Day 2 ✅ |
| `seckill:order:dead:stream` | Stream | 消费失败死信 | 第3阶段 Day 4 ✅ |
| `seckill:lock:preheat:{activityId}` / `seckill:lock:reset:{activityId}` | String(锁) | 预热/库存重置分布式锁 | 第3阶段 Day 5 ✅ |
| `seckill:rank:{activityId}` | ZSet | 秒杀成功榜（member=userId，score=抢单成功时刻） | 第4阶段 Day 1 ✅ |
| `seckill:metric:{activityId}` | Hash | 活动拒绝计数（rateLimit/duplicate/soldOut 字段） | 第4阶段 Day 2 ✅ |

---

## 六、接口状态（当前阶段）

| 方法 | 路径 | 状态 | 计划实现日 |
|------|------|------|-----------|
| GET | `/api/health` | ✅ | Day 3 |
| GET | `/api/products/{productId}` | ✅ | Day 4 |
| POST | `/api/admin/products` | ✅ | Day 4 |
| POST | `/api/admin/seckill/activities` | ✅ | Day 6 |
| POST | `/api/admin/seckill/activities/{id}/preheat` | ✅ | Day 6 |
| GET | `/api/seckill/activities/{id}` | ✅ | Day 6 |
| GET | `/api/seckill/activities/{id}/check` | ✅ | Day 6（审计整改 R3 后需 `X-User-Token`） |
| POST | `/api/seckill/execute` | ✅ | 第2阶段 Day 2/5（Lua 原子整合后完成；R3 后需 `X-User-Token`） |
| GET | `/api/seckill/orders/{orderNo}` | ✅ | 第3阶段 Day 3（未落库返回 QUEUING；C1/C2 后按 `OrderStatusEnum` 输出 `status`/`statusDesc`，键名 `createdAt`） |
| GET | `/api/rank/top10` | ✅ | 第4阶段 Day 1（榜单 Top N，`?activityId=` 必填） |
| GET | `/api/admin/seckill/activities/{id}/metrics` | ✅ | 第4阶段 Day 2（活动实时运行指标） |
| POST | `/api/admin/seckill/activities/{id}/snapshot` | ✅ | 第4阶段 Day 2（手动打点指标快照） |
| POST | `/api/support/chat` | ✅ | 增强② AI 客服（外部 OpenAI 兼容大模型，未配置密钥返 50300） |
| GET | `/api/seckill/users/{userId}/orders` | ✅ | 审计整改 C3（interface 4.10，`?activityId=` 可空；R3 后需 `X-User-Token` 且令牌绑定的 userId 须匹配路径） |
| POST | `/api/admin/seckill/dead-letters/replay` | ✅ | 审计整改 R5（死信人工回放：按 activityId+orderNo 列表回放，需 `X-Admin-Token`） |
| POST | `/api/auth/register` | ✅ | 今日卡片 G1/G2（动态发令牌：自定义 userId 领取服务端随机令牌，内存注册表占用返 40903；见 interface 4.15） |

> 鉴权说明（审计整改 R3 后生效）：`/api/admin/**` 需请求头 `X-Admin-Token`（= `security.admin-token`，建议环境变量注入）；`/api/seckill/execute`、`/api/seckill/activities/{id}/check`、`/api/seckill/users/{userId}/orders` 需请求头 `X-User-Token`（格式 `security.user-tokens`，`token:userId,` 逗号分隔，服务端据令牌解析出 userId，不再信任调用方传入的 userId）。

**配置收敛（2026-09-08）：环境变量统一由 `.env` 管理**
- 后端 `pom.xml` 引入 `me.paulschwarz:spring-dotenv:4.0.0`，应用启动与 `@SpringBootTest`（含命令行 `mvn test`）均自动读取进程工作目录（IDEA/命令行都以 `E-commerceFlashSaleSystem` 模块目录运行）下的 `.env`；优先级：真实系统环境变量 > `.env` > `application.yaml` 默认值。
- `E-commerceFlashSaleSystem/.env`（模板 `.env.example`）收敛：`MYSQL_PASSWORD`、`ADMIN_TOKEN`、`USER_TOKENS`、`OSS_ENABLED/ENDPOINT/ACCESS_KEY_ID/ACCESS_KEY_SECRET/BUCKET_NAME/DOMAIN`、`AI_LLM_BASE_URL/API_KEY/MODEL`。
- `frontend/.env`（模板 `.env.example`）收敛：`VITE_ADMIN_TOKEN`、`VITE_USER_TOKEN`（Vite 只注入 `VITE_` 前缀，值须与后端对应令牌一致）。
- 根 `.gitignore` 忽略 `.env` / `.env.*`，仅放行 `.env.example`，密钥不入库。
- 生效细节：后端 `.env` 每次启动/测试重读，无需重启；前端 `.env` 为构建期替换，改动后须**重启** `npm run dev`。
- 收益：此前"命令行 `mvn test` 缺 `MYSQL_PASSWORD`/`AI_LLM_API_KEY` 环境变量"导致的 19 个 `Access denied (using password: NO)` Error 与 AI 客服用例 50300 失败彻底消除——现命令行全量回归 **77 用例 BUILD SUCCESS**。

---

## 七、Git 提交建议节点

当完成一个 Day 的全部任务并通过编译验证后，建议进行一次 Git 提交。例如：

```
完成 Day 2：实体模型与数据访问层

- 创建 Product、SeckillActivity、SeckillOrder 实体
- 创建 4 个状态枚举（ActivityStatus、OrderStatus、PreheatStatus、ProductStatus）
- 创建 3 个 Mapper 接口及 XML 映射文件
```

---

### 今日任务（2026-09-08 · ✅ 已完成并回归）：多用户演示账号体系 — 修复「仅 1001 能参与秒杀」

> **问题背景**：R3 鉴权（commit `49a0b09`）后，后端 `execute` / `check` / `users/{id}/orders` 三处强制「`X-User-Token` 解析出的绑定 userId == 请求中的 userId」。而前端 `http.js` 对所有用户请求**固定携带单令牌** `VITE_USER_TOKEN`（=`user-a`，绑定 1001），顶栏「演示用户」却可自由切换任意 userId → 一旦切成非 1001，令牌仍解出 1001，即 401「请求用户与访问令牌不匹配」。表现：**1001 正常参与，其余用户全部无法参与秒杀**。
>
> **需求确认（panda）**：演示用户账号**可随意配置** —— 演示账号（userId + 令牌 + 显示名）由配置驱动，增减用户不改业务代码。
>
> **改动清单（认领：panda 实现，导师审查）：**

| # | 任务 | 落地要点 | 验收口径 |
|---|---|---|---|
| F1 | 后端令牌账号可配置扩展 | `E-commerceFlashSaleSystem/.env` 与 `.env.example` 的 `USER_TOKENS` 扩至演示所需（默认 `user-a:1001` ~ `user-f:1006` 六账号，逗号分隔追加）；改动后需重启后端生效 | 1002~1006 均能通过用户接口令牌校验，不再 401 |
| F2 | 前端演示账号表（配置驱动） | 集中定义演示账号表（如 `store.js` 常量）：`[{ userId:'1001', token:'user-a', name:'演示用户A' }, …]`，与后端 `USER_TOKENS` 一一对应；**增删演示账号 = 改 .env 一行 + 表一行**，不改业务代码 | 表项与后端账号对齐；新增/删除一行即可扩展演示用户 |
| F3 | 请求令牌随当前账号携带 | `frontend/src/api/http.js` 的 `X-User-Token` 由当前 `userStore.userId` 查账号表得出；表中无此用户时回落到 `VITE_USER_TOKEN`（仍不匹配时由后端 401 文案兜底） | 切换任意演示账号，check/execute/查订单均不再报「令牌不匹配」 |
| F4 | 顶栏「演示用户」账号化 | `App.vue` 改为从账号表渲染「演示用户」下拉（显示名 + userId），保留手动输入但**无绑定令牌时明确提示**「该用户未配置访问令牌，仅可浏览不可抢购」 | 界面清晰不误导；不会再出现无令牌 userId 的一连串 401 |
| F5 | JMeter 多用户压测配套提醒 | 多用户压测脚本每线程的 `X-User-Token` 须与其 body/参数 userId 匹配（CSV 放 `token,userId` 双列），并在压测脚本/说明处注明，避免单令牌误用 | 压测脚本与说明不再有「单令牌跑多用户」的误导 |

> **回归口径**：✅ 已达成 —— 全量 `mvn test` **77 用例 BUILD SUCCESS**；`npm run build` 通过、前端 0 lint；真实 HTTP 冒烟：`user-b@1002` / `user-f@1006` 查单 `code=0` 通过鉴权，`user-a`（绑定 1001）访问 1002、以及未注册令牌均 40100 拒绝；顶栏下拉切换 1001~1006 即时生效，自定义无令牌 userId 有明确提示（仅可浏览）。
>
> **备注**：本项不改后端鉴权语义（R3「令牌强绑定 uid、不信任裸传 userId」保持），属前端「单令牌 → 多账号令牌按用户携带」的回归修复；若将来确需任意 userId 直通，须先回退 R3 校验与相关测试，不建议。

---

### 今日任务（2026-09-09 · ✅ 已完成并回归）：动态发令牌注册接口 — 免去「加账号改两处 + 重启」的手动注册

> **分工变更（panda）**：自本卡起后续功能由 AI 完成实现，panda 只做最终审查（本卡按 AI 执行记录）。
>
> **问题背景**：多用户账号体系（F1~F5）后，新增一个演示用户仍需「后端 `.env` `USER_TOKENS` 追加一行 + 前端 `DEMO_ACCOUNTS` 追加一行」两处同步并重启后端，演示/审查成本高；R3 鉴权链没有「运行时注册」入口，前端「自定义用户 ID」只能浏览、无法抢购。
>
> **需求确认（panda）**：新增「动态发令牌」注册接口——自定义 userId 直接向服务端领取随机令牌，免去静态配置步骤。

| # | 任务 | 落地要点 | 验收口径 |
|---|---|---|---|
| G1 | 后端：动态令牌注册表组件 + 拦截器「静态 ∪ 动态」 | `UserTokenRegistry`（静态白名单 ∪ 动态双向索引 `ConcurrentHashMap`；占用即拒；服务端 32 位随机令牌）；`ApiAccessInterceptor.requireUser` 委托注册表解析（不再自行 split）；顺带修复上次中断遗留的编译错误（`SecurityProperties` 重复字段、拼写错误的半成品 `UserTokenRegisty`） | `mvn compile` 通过；注册表/拦截器单测全绿 |
| G2 | 后端：`POST /api/auth/register` 注册接口 | `RegisterRequest`/`RegisterResponse` + `UserAuthController`（新增 `controller/auth` 包）；`ResultCode` 增 `USER_ID_TAKEN(40903)`；匿名可调（位于拦截路径之外），成功返回 `{userId, token}` | 注册成功 / 静态与重复占用 40903 / 缺参 40001 语义正确 |
| G3 | 后端：注册/防冒领/回归测试 | `UserTokenRegistryTest`（+6，含并发抢注唯一成功）/ `ApiAccessInterceptorTokenTest`（+4，静态+动态放行、无效 40100）/ `AuthRegisterIntegrationTest`（+5，MockMvc 真实 HTTP：动态令牌解锁受保护 `execute`） | 全量 `mvn test` 92 用例 BUILD SUCCESS |
| G4 | 前端：顶栏「领取令牌 / ✓ 已注册」交互 | `store.js` 本地缓存 `localTokenOf`/`rememberToken` + `tokenOf` 两级查找；`api.registerUser`（userId 字符串透传避免 JS 大数精度丢失）；`App.vue`「领取令牌」主按钮 → 已注册态「✓ 已注册」（可点重领，应对后端重启内存清空） | `npm run build` 通过、前端 0 lint；自定义 userId 领令牌后抢购不再 401 |
| G5 | 文档与验证 | `interface.md` 补 4.15 注册接口 + 40903 错误码 + 接口清单行；本卡记录 | 全量回归 + 前端构建通过 |

> **回归口径**：✅ 已达成 —— 全量 `mvn test` **92 用例 BUILD SUCCESS**（77 + 新增 15，0 Failures / 0 Errors）；`npm run build` 通过；集成测试日志实证动态令牌穿过 R3 拦截器进入业务层。
>
> **备注**：动态注册令牌存后端内存、重启即失效（前端「✓ 已注册」可点击重领刷新本地缓存）；静态演示账号（1001~1006）仍由 `.env` 白名单提供且不可被动态抢占（40903），静态与动态共享同一 `UserTokenRegistry` 鉴权身份源。

---

### 交付前收尾（2026-09-09 · ✅ 已 git 提交 `c1f689e`）：S2 Redis 加固 + 审查整改 M1~M10

> 承接 F/G 多账号卡后的交付前收尾批量。S2 加固与 M 系列审查整改已实现，并连同 F1~F5/G1~G5 一并提交（HEAD `c1f689e`），工作区 clean、与 origin/main 同步；提交后全量 `mvn test` **92 用例 BUILD SUCCESS**（含新增 `AdminSeckillResetStockTest` +4，E1-r 管理端 reset-stock）。

**S2 · Redis 加固（安全）：**

| 项 | 落地要点 | 验证口径 |
|---|---|---|
| 反序列化白名单 | `RedisConfig`：DefaultTyping 的 `LaissezFaireSubTypeValidator`（放任一切类型）改为 `BasicPolymorphicTypeValidator`，仅放行 `com.ghb.ecommerceflashsalesystem.` 实体包 + `java.time./util./lang./math./net.`——Redis 数据被污染也无法引导实例化任意 gadget 类 | 机制保留、教学注释写明动机；全量回归 92 绿 |
| 连接密码可配 | `application.yaml` `spring.data.redis.password: ${REDIS_PASSWORD:}`，`.env` / `.env.example` 增 `REDIS_PASSWORD` 项（本地留空 = 本机无密码） | 不写死、不落明文 |

**M 系列 · 交付前审查整改（契约 / 口径 / 体验收敛）：**

- execute 失败响应 `data.result` 契约细分、`requestId` 对齐 interface；
- 缓存未命中改按实时时间窗细分拒绝（与 check/execute 开放口径同源）；
- AI 客服目录过滤过期活动、活动状态文案实时推导（对齐前端本地时间窗判定）；
- 列表 `preheated` 预热标记；预热入口收敛到管理台；
- 订单查询支持可选归属校验（`OrderController`）；详情页「我的抢购记录」面板；
- 新增 `AdminSeckillResetStockTest`（+4，覆盖 E1-r 管理端 reset-stock 守卫语义：活动进行中 `PARAM_ERROR` 拦截且不触碰 Redis、非进行中可正常重置，全量 92 内含）。

> **记录口径**：M1~M10/S1/S2 逐项编号映射的审查底稿已留档于 `docs/final-review.md`（含截至 2026-09-09 的逐条复核状态标注；`docs/audit-report.md` 保留 2026-09-08 的 R/C/E 全量审计版）。S2/M 落地代码位置：`config/RedisConfig.java`、`application.yaml`、`.env.example`、`controller/`、`service/impl/SeckillServiceImpl.java`、`service/impl/AiChatServiceImpl.java`、`domain/vo/ActivityItemVO.java`、`integration/AdminSeckillResetStockTest.java`。

---

### 第 4 阶段 Day 6 — 复盘总结与学习收口 ✅（2026-09-09，文档收尾，无代码改动）

> 分工变更后由导师（AI）实现、panda 终审。本日不新增功能，只做学习收口与文档交付——4 周计划（developlan）要求的「Redis 解决方案映射文档」「一篇自己的《Redis 实战总结》」在此落地。产出后全量 `mvn test` **92 用例 BUILD SUCCESS**（文档改动不涉代码，回归即基线）。

**学习收口三件套：**

| 交付 | 状态 | 内容 |
|---|---|---|
| `docs/redis-summary.md`《Redis 实战总结》 | ✅ 新增 | 六类 Redis 企业问题 × 模式 × 真实代码位置索引 × 关键设计 × 踩坑 × 验证证据链；秒杀主链路一站一模式对应；附可靠性/幂等横切、交付加固（R3 鉴权 / S2 白名单 / .env 密钥）与文档关系表 |
| `docs/notework.md` 学习笔记沉淀 | ✅ 扩充 | 保留原《并发断言》，新增 8 个主题「自己的话」笔记：Lua 原子性 / 缓存三兄弟 / 幂等四道闸与售罄回滚 / Stream 可靠性 / 滑窗限流 / 分布式锁取舍 / Redis 序列化类型坑 / 工程环境坑 |
| `docs/plan.md` 收尾 | ✅ 本卡 | 路线图「⏳ 剩余 Day 6」清除 → 4 阶段全部完成；本卡记录 |

**复盘沉淀要点（更细经验教训见 redis-summary/notework）：**
- **六类模式各归其位**：高并发读（缓存旁路 + 三防护）→ 防超卖（Lua 原子）→ 防重复（SETNX 令牌 + DB 唯一键）→ 削峰（Redis Stream）→ 防刷（滑窗 ZSet）→ 排行（Sorted Set NX）；分布式锁只护「多步读改写」写入口，execute 主链由 Lua 承担原子性。
- **「落库才算抢到」是全局一致的主线**：令牌是排队资格、订单落库才入榜、失败须补偿令牌/库存——一切以 DB 终态为准（审计 R2/R5 同源）。
- **本工程反直觉教训 Top3**：Redis 反序列化 `@class` 需白名单（S2）；压测/集成回归前必须停 dev（auto-poll 抢消费）；Stream 消息用明文 Map 防 Base64 二次编码。

---

## 八、第 5 阶段：微服务架构演进（P0~P4 已完成 · P5 规划中）

> 方案与 P0 详细任务卡：**`docs/microservices-plan.md`**（2026-10-12 落档：迁移策略 / 服务划分 / 技术选型 / 阶段规划 / 质量运维 / 风险 / 时间线 + P0 逐日步骤）。
>
> 本阶段定位：在单体（4 阶段全部完成、92 用例基线）之上做「模块化单体 → 绞杀者式微服务抽取」的渐进演进；坚持**可停可回退**，任意阶段停下都是完整可演示系统。

**阶段路线（P0 ~ P6）：**

| 阶段 | 主题 | 预估 | 里程碑 | 状态 |
|---|---|---|---|---|
| P0 | 边界治理（Maven 12 模块 + 包边界 + ArchUnit 规则） | 4 天 | M1 模块化单体 | ✅ 完成（2026-10-13） |
| P1 | 基础设施（Nacos / Gateway / Compose / 追踪 / 监控） | 3 天 | M2 基建就绪 | 🔄 P1-1 ✅ / P1-2 进行中（T1 ✅） |
| P2 | 边缘抽取（auth-service / ai-service / 网关统一鉴权） | 4 天 | M3 首个服务上线 | ⏳ |
| P3 | 商品与活动（product / activity 独立库与缓存所有权） | 5 天 | M4 主数据域独立 | ⏳ |
| P4 | 核心交易（seckill / order / `order.created` 事件 / 数据迁移） | 6 天 | M5 跨服务交易闭环 | ✅（**Day 1 切读 / Day 2 seckill / Day 3 order / Day 4 order 库拆分 / Day 5 ai 摘出旧库 + 测试隔离 / Day 6 auth 库拆分 + 清理归属收口 / Day 7 会话校验跨服务化 / Day 8 analytics 订单读路径跨服务化 / Day 9 Redis 隔离 / Day 10 收官（旧库 `flash_sale` 已 DROP）**（2026-10-16~17）；检查表清零；剩余仅"删镜像死代码"一项卫生工作） |
| P5 | 读模型收口（analytics 事件驱动，CQRS 完成） | 3 天 | M6 读模型分离 | ⏳ |
| P6 | 质量运维（CI/CD / 告警 / 混沌演练 / Runbook） | 4 天 | M7 可运维交付 | ⏳ |

**规模口径**：8 服务 + 1 网关（gateway / auth / product / activity / seckill / order / analytics / ai）封顶；同步用 OpenFeign、异步复用 Redis Stream（进阶可换 Kafka）；注册与配置用 Nacos，网关用 Spring Cloud Gateway，限流熔断用 Sentinel。

**与单体计划的衔接：**
- 第 1~3 阶段的 Lua / Stream / PEL / 死信 / 补偿资产原样进入 seckill / order 服务；
- 第 4 阶段的榜单 / 指标 → P5 改为事件驱动读模型；
- `scripts/jmeter` 5000 并发脚本 → P4 复测基准（改指向网关）；
- `interface.md` 契约与 `Result` 信封冻结，前端只改 `VITE_API_BASE_URL`；
- 92 用例基线按模块归位，作为每阶段回归红线。

**开工前置**：①先提交当前工作区未提交内容（用户账号体系 / 前端 TS 新架构 / 文档变更），打 `mono-baseline` 标签；②P0 按 `docs/microservices-plan.md` 第九节 Day 1~Day 4 逐日执行（每步绿 + 每步提交）。

**P0 任务卡要点（已就绪，见方案文档第九章）**：12 模块目录树 · 模块依赖矩阵 · 现有代码逐文件迁移映射（含 DTO/VO、Mapper XML、Lua 归属）· 父 POM 与子模块 POM 模板 · 配置调整清单（`@MapperScan` 5 域包 / `classpath*:mapper/*.xml` / `.env` 工作目录）· ArchUnit 12 条规则 · 过渡白名单（鉴权装配、`AdminSeckillController`、`HealthController`、共享实体）· 逐日步骤与回退策略。

**P0 逐日进度（模块化单体）：**

| 日 | 任务 | 状态 | 验证 / 提交 |
|---|---|---|---|
| 前置 | 基线冻结：提交工作区（4 个逻辑提交）+ `mono-baseline-20261012` 标签 | ✅ 2026-10-12 | `345e1c6` |
| Day 1 | 多模块骨架：父 POM（`flash-sale-parent`）+ 12 模块 + `src/` 整体迁入 `flash-sale-bootstrap` + surefire/run 工作目录治理 | ✅ 2026-10-12 | `mvnw test-compile` / `package` 通过；**全量 97 用例 BUILD SUCCESS**；提交 `5e23798` + 测试修正 `867e1d7` |
| Day 2 | 抽取 common / contracts / domain-model / infra（含 `infra.**` 包重构、空目录清理、`PageResult`/`PageResponse` 二选一） | ✅ 2026-10-13 | 模块抽取提交 `f717a7f`/`b02b383`/`8ebec4e`/`1a50ba5`；`PageResult` 判定死代码删除（分页统一 `PageResponse`）；**全量 97 用例 BUILD SUCCESS** |
| Day 3 | 抽取 user / product / activity（含 DTO/VO、Mapper XML、OSS 依赖归位） | ✅ 2026-10-13 | 两次原子提交 `b0f777d`（user + `PageResponse` 上移 common）/ `1898947`（product + activity，含 2 个 Mapper XML）；全量 97 用例 BUILD SUCCESS |
| Day 4 | 抽取 seckill / order / analytics / ai + bootstrap 收薄 + ArchUnit 规则 + 终验 | ✅ 2026-10-13 | 三次提交 `328c5de`/`691b6c4`/`fffcb4d`；bootstrap 仅剩启动类 + 4 个白名单组件；**ArchUnit 16 条断言全绿**；全量 **113 用例 BUILD SUCCESS**；`git tag p0-done` |

> 全量回归（2026-10-12 补跑）：停 dev 后端后 `mvnw clean test` → **97 用例 BUILD SUCCESS**（较 9 月的 92 增长 5，来自用户体系测试）。过程中发现并修正 1 处**基线既有**的过期测试：`AuthRegisterIntegrationTest` 会话态 `execute` 写请求须携带 `X-CSRF-Token`（CSRF 校验先于身份绑定，漏带头会返回 40301 且全局响应无 `data`），提交 `867e1d7`。
> Day 1 踩坑记录：`spring-boot:run` 不可写成 `<executions>`——会被 Maven 绑定进生命周期，导致 `mvn test-compile` 也启动应用（撞 8081 端口）；正确做法是插件级 `<configuration><workingDirectory>${maven.multiModuleProjectDirectory}</workingDirectory>`。
> Day 2 踩坑记录：①跨模块后"同包免 import"不再成立——`GlobalExceptionHandler` 迁出 `common.exception` 后必须显式 `import BusinessException`，否则编译报"找不到符号"（IDE 重构会自动补，命令行迁移要人工核对）；②PowerShell 中 `git mv "目录"/*.java` 通配符不会展开（原生命令不走 PowerShell 通配），需 `Get-ChildItem | ForEach-Object { git mv ... }` 逐个迁移；③`PageResult` 经全库引用扫描确认零引用（死代码），直接删除并把分页结构统一为 `PageResponse`。
> Day 3 踩坑记录：①**归属调整 3 例**——`PageResponse` 上移 `common.api`（否则 user→bootstrap 循环依赖）、`SecurityProperties` 随会话域从白名单提前归位 `user.config`（`UserSessionService` 依赖它）、`ActivityCheckResponse` 归 `activity`（`check` 由活动域产出，Day 4 的 seckill 通过 seckill→activity 依赖边取用）；②**新依赖边**：`activity → product`（活动创建需校验商品存在，走 `ProductMapper`；文档矩阵已同步）；③**多模块 Mapper 扫描**：`@MapperScan` 增至 4 个包，`mapper-locations` 必须改 `classpath*:mapper/*.xml`（否则只扫到 bootstrap 的 XML）；④同包免 import 问题在反向方向再次出现（`ApiAccessInterceptor`/`ApiAccessWebConfig` 需显式 import `SecurityProperties`/`UserTokenRegistry`）。
> Day 4 踩坑记录：①**解环设计**——analytics 需要订单数据、order 需要写榜单，直接互赖成环；引入端口倒置（analytics 定义 `OrderQueryPort`，order 提供 `OrderQueryAdapter`）后依赖方向单一；②**鉴权常量上移**——控制器原先引用 bootstrap 的 `ApiAccessInterceptor.AUTHENTICATED_USER_ID`，形成"业务→装配层"反向依赖，常量上移 `common.constant.AuthAttributeConstant`；③**控制器直连 Mapper 两处**（OrderController / ProductController）按分层规则收敛到服务层（新增 `OrderQueryService`、`ProductService.listOnShelf`）；④**共享技术资产不硬塞业务域**——Lua 脚本与 3 个 `RedisScript` Bean、`SeckillOrderStreamProducer` 被 seckill 与 order 共同使用，保留在 infra（避免 order→seckill 边），P4 再收口为 seckill-service 内部接口；⑤**ArchUnit API 细节**——`slices()` 位于 `library.dependencies.SlicesRuleDefinition`；命名规则需用谓词排除内嵌类（如 `UserSessionService$AuthenticatedSession`）；⑥**PowerShell 运算符优先级**——`@($X+'a',$X+'b')` 会被解析为单元素（逗号优先级高于 `+`），数组构造避免 `+` 与 `,` 混用（当日曾因此导致一次"0 文件替换"，靠文件抽查及时确认未损坏）。

**P1 分批进度（基础设施）：**

| 批次 | 任务 | 状态 | 验证 / 说明 |
|---|---|---|---|
| P1-1（免容器） | ① `flash-sale-gateway` 网关模块（`/api/**` 静态路由 → 单体 8081、CORS、traceId 注入，端口 8090）② 单体 Actuator + Prometheus 端点 ③ **traceId 三处贯通**（HTTP → MDC → Stream 消息体 → 消费端 MDC）④ 前端代理改走网关 | ✅ 2026-10-13 | 网关 200 + 单一 `X-Trace-Id`；同一链路号同时出现在**网关 / 生产线程 `http-nio-8081-exec-5` / 消费线程 `scheduling-1`**；`/actuator/prometheus` 200；前端经网关复验商品与活动实时渲染；全量 113 用例 BUILD SUCCESS |
| P1-2（本机 Nacos + WSL Docker） | ① Nacos **本机原生安装**（2.5.4 standalone，非容器）② 单体注册 Nacos ③ 网关 `lb://` 动态路由 ④ Prometheus/Grafana 观测栈（WSL Docker，T5）⑤ OTel/Zipkin 可选 | ✅ **完成（T1~T5 + 全量绿线）**；T6（OTel/Zipkin）为可选增强，未做 | 提交：T2 `a36e38f` / T3 `67ca26d` / T4 `8de0ef7` / T5 `289e7e3`；停应用后全量 `mvnw clean test` → **113 用例 0 失败 BUILD SUCCESS**；Docker：`redis` 发行版 Engine 29.7.2 + Compose v5.5.0（`my-redis` 占用 6379，勿重复声明）；Nacos：8848+9848，未开鉴权，内嵌 Derby |

> P1-1 踩坑记录：①**端口冲突**——本机 8080 被外部服务占用（uvicorn，疑似 WSL 端口转发，非本项目），网关改用 `${GATEWAY_PORT:8090}`，前端 vite 代理同步指向 8090；②**响应头重复**——网关与单体都回填 `X-Trace-Id`，网关侧提前 `set` 会与下游头合并成两条，改为 `beforeCommit` 提交前覆盖写；③**日志级别**——单体 `com.ghb...` 由 ERROR 调回 INFO（链路贯通与缓存命中观测需要业务日志可见）。

**P1-2 任务进度（panda 实现，导师审查）：**

| 任务 | 内容 | 状态 | 证据 / 验收 |
|---|---|---|---|
| T1 | WSL Docker 环境确认 | ✅ 2026-10-14 | `redis` 发行版内 Docker **29.7.2** + Compose **v5.5.0**；现有容器 `my-redis`（redis:latest，6379 已发布）；`networkingMode=mirrored`（WSL 端口直接镜像到 Windows localhost） |
| T2 | ~~Compose 基建骨架~~ → **修订（2026-10-14，panda 决策）**：Nacos **本机原生安装**（非容器、不走 WSL）；Prometheus/Grafana 观测栈保留到 T5（WSL Docker） | ✅ 2026-10-14 | 证据：Nacos **2.5.4** / standalone / 未开鉴权 / 8848+9848 双端口 / 内嵌 Derby；`deploy/README.md` 已交付（安装路径 `D:\nacos-server-2.5.4\nacos`、启停脚本、**自启现状＝未配置**〔服务/计划任务/启动夹/注册表四处核查〕+ 两种自启方案、注册信息、排查 4 条、T5 预留） |
| T3 | 单体注册进 Nacos（父 POM 加 SCA BOM + bootstrap 加 discovery + 配置/测试资源） | ✅ 2026-10-14（**由导师直接实现，panda 指定**） | `doms:["flash-sale-monolith"]`；实例 `172.21.112.1:8081` / `healthy:true`；注册 IP 与 127.0.0.1 均 200 可达；全量 **113 用例 BUILD SUCCESS**；测试侧注册关闭（跑完服务列表为空） |
| T4 | 网关 `lb://` 服务发现路由 | ✅ 2026-10-14（导师实现） | 网关加 `nacos-discovery` + `loadbalancer`，路由 `uri` 由静态改为 `lb://flash-sale-monolith`；经网关访问健康检查 200（`X-Trace-Id` 仍贯通）、商品列表 JSON 正常；Nacos 服务列表 2 个（`flash-sale-gateway` / `flash-sale-monolith`）；全量 113 用例 BUILD SUCCESS；⚠️ 运维提示：单体未注册时网关返回 503 |
| T5 | Prometheus 抓取单体/网关 + Grafana 看板 | ✅ 2026-10-14（导师实现，提交 `289e7e3`） | Targets 全 UP（3/3）+ 看板出图；数据源/看板全部 provisioning 自动加载（`uid=prometheus` / `flash-sale-obs`，6 面板）；`deploy/` 新增 6 文件 + `README.md` 观测栈章节 |
| T6（可选） | OTel/Zipkin 接入，与自研 traceId 打通 | ⏳ 未做（可选增强，可留待后续） | Zipkin 可查 execute 链路 span |

> **P1-2 收尾绿线（2026-10-14）**：先停单体 `:8081` / 网关 `:8090`（避免 `flash.stream.auto-poll=true` 抢消费集成测试写入 Stream 的消息），再执行 `.\mvnw.cmd clean test` → **113 用例 / 0 Failures / 0 Errors / 0 Skipped / BUILD SUCCESS（耗时 1.1 分钟）**，14 个模块全部 SUCCESS；环境复核：停用后仅 Nacos（8848/9848）在监听、MySQL/Redis 在线、观测栈容器与被测进程互不影响。恢复演示环境后：经网关健康检查 200、Nacos 实例 `healthy:true`、Prometheus targets 3/3 UP（Grafana 恢复出图）。→ **P1-2 全部收口（T1~T5 + 绿线）**，可进入 P2 边缘抽取。

> P1-2 注意事项（导师提示）：①Compose v5 下 `version:` 字段已废弃，不要写；②`my-redis` 为 `docker run` 启动，compose 中不得重复声明 redis 抢 6379；③Nacos 2.x 必须映射 **9848**（客户端 gRPC），否则注册失败；④观测栈必须 `network_mode: host`（实测本机 `networkingMode=mirrored` 下 **bridge 容器访问不到 Windows 宿主应用**：`host-gateway` 与直连 WSL vEthernet IP 均超时）；Windows 侧访问须写 `127.0.0.1`（Prometheus/Grafana 仅监听 IPv4，`localhost` 优先走 `::1` 会超时）；⑤**Maven 镜像必须用 HTTPS**（2026-10-14 阻塞性环境问题，已修复）：本机 `~/.m2/settings.xml` 的 alimaven 镜像为 `http://maven.aliyun.com/nexus/content/groups/public/`，被 Maven 3.8+ 的 HTTP 拦截机制阻断（报 `Blocked mirror for repositories`），导致**本地仓库没有的新依赖无法下载**（Spring Cloud Alibaba BOM 解析失败即由此引起；此前构建能过是因为依赖早已在本地仓库）→ 已改为 `https://maven.aliyun.com/repository/public`。

---

**P2 逐日进度（边缘抽取）：**

| 日 | 任务 | 状态 | 验证 / 提交 |
|---|---|---|---|
| Day 1 | `flash-sale-auth` 可启动骨架（新模块 + 启动类 + 独立配置 + 父 POM 登记），**零行为变更** | ✅ 2026-10-14 | `install` 通过；`:8082` health **UP**（`db` / `redis` / `nacosDiscovery` 全 UP）；Nacos 服务列表 **3 个**（新增 `flash-sale-auth`）；`POST /api/auth/login` 返回业务错误码 `40101`（域代码在新进程已生效）；单体/网关零影响（`gw_health=200`、`gw_products=200`） |
| Day 2 | auth 域割接：把 `user` 的 controller / service / mapper / dto 迁入 `flash-sale-auth`，单体摘除该域；网关加 `/api/auth/**`、`/api/admin/users/**` → `lb://flash-sale-auth`（置于 catch-all 之前） | ✅ 2026-10-14 | Nacos **3 服务**；经网关注册/登录/`me` → `code=0`；`/api/admin/users` 带令牌 `code=0`（total=42）/无令牌 `40100`；**单体已摘除**（带有效会话/令牌直连 8081 均 **404**；未带凭证时被拦截器提前拒绝为 `40100`，非业务响应，属预期）；`/api/seckill/.../check` 仍 `40100`（单体自持鉴权）；`install` exit=0、core 单测 **13/13**、`ArchitectureTest` **16/16** |
| Day 3 | ai 域割接：`flash-sale-ai` 变为可启动服务（含 `AiLlmProperties` / `LlmChatClient` / `AiChatServiceImpl`；过渡期目录数据仍直连 DB）；网关加 `/api/support/**` → `lb://flash-sale-ai` | ✅ 2026-10-14（导师实现） | Nacos **4 服务**（gateway / auth / ai / monolith）；经网关 `POST /api/support/chat` 空体 → `40001`（路由 + 参数校验生效，**未触发大模型调用**）；直连单体同路径 → **404**；Prometheus targets **5/5 UP**；全量回归 **115 用例 0 失败 BUILD SUCCESS** |
| Day 4 | 网关统一鉴权（管理令牌 / 用户会话下沉网关过滤器）+ 全量回归 + 文档 + `git tag p2-done` | ✅ 2026-10-14（导师实现） | 网关准入生效：admin 无令牌 / 令牌未配置 → 网关 `40100`（日志 `gw 准入拒绝`）、用户态无凭证 → 网关 `40100`；**服务侧纵深防御仍在**（直连单体 `/api/admin/products/1` → `40100`）；**CSRF 细校验仍由服务执行**（有效会话 execute 缺头 → `40301`）；经网关带令牌 → `code=0`；公开路径 `/api/products` → 200；`X-Trace-Id` 单值；网关单测 **9/9**；全量回归 **124 用例 0 失败 BUILD SUCCESS** |

> P2 过渡期约定（Day 1 已生效）：① 数据库仍共用 `flash_sale` schema（P3 再做 schema 隔离与双写迁移）；② 新服务内暂缺鉴权拦截器，由 Day 4 的网关统一鉴权承接；③ **回退点**：停掉 `flash-sale-auth` 进程即回退到单体全量行为（网关路由未改）。

### P2 Day 2 详细任务卡（方案 A：auth 域割接）

> 执行方式：panda 实现 + 导师现场纠偏（本条按 panda 要求以"指导"形式交付）；预估 2~3 小时。

**目标形态**：auth 域实现收敛到库 `flash-sale-auth-core`；HTTP 接口由 `flash-sale-auth`（:8082）独家提供；单体摘除该域、仅保留自持鉴权；网关精确导流。

```
flash-sale-auth-core（新库）                flash-sale-auth（app :8082）
├─ user/config/*   (3) 安全配置 / 令牌       ├─ user/controller/* (2) ← 唯一对外接口方
├─ user/service/*  (2) 账号 / 会话服务       ├─ AuthServiceApplication
├─ user/mapper/*   (2)                      └─ application.yaml（Day 1 已就绪）
├─ user/dto/**     (7)
└─ user/web/*      (2) 拦截器对（自 bootstrap/config 迁入并改包名）
        ▲                                          ▲
   flash-sale-bootstrap（依赖 core：仍自持 /api/admin/**、/api/seckill/** 鉴权，不再暴露 auth 接口）
```

**Day 2 侦察结论（决定方案形态）**：① 拦截器链 `ApiAccessInterceptor → UserSessionService → UserAccountService` → "横切库"必须承载**整个 auth 域实现**（不能只放 4 个类）；② 跨模块引用 `user.*` 仅 **5 处**（bootstrap 的 2 个 config 类 + 3 个测试类）→ 搬迁面小；③ 仅 `AuthRegisterIntegrationTest` 触碰 `/api/auth` → 测试归位可控。

| 步骤 | 内容 | 要点 / 命令 |
|---|---|---|
| Step 0 前置 | 停 8081/8090（改结构前保持干净）；确认工作区 clean | `Get-NetTCPConnection -LocalPort 8081,8090 -State Listen \| ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }` |
| Step 1 建 core 库 | 新建 `flash-sale-auth-core`；`git mv` 搬 `user/{config,service,mapper,dto/**}`（14 文件）+ 把 bootstrap 的 `config/{ApiAccessInterceptor,ApiAccessWebConfig}` 搬入并改包名 `...user.web`；POM 依赖 common / domain-model / infra / security-crypto（+test）；父 POM 的 `<module>` 与 `dependencyManagement` 中 `flash-sale-user` → `flash-sale-auth-core` | 除拦截器对外，**包名保持不变**（最小化 import 改动）；`spring-webmvc`/`jakarta.servlet` 由 infra 传递 |
| Step 2 auth 承载接口 | `user/controller/*`（`UserAuthController`、`AdminUserController`）`git mv` 进 `flash-sale-auth`；auth POM 依赖改 `flash-sale-auth-core`；删除 `flash-sale-user` 空模块 | 单体从此不再暴露 `/api/auth/**`、`/api/admin/users/**` |
| Step 3 单体收窄 | bootstrap POM：`flash-sale-user` → `flash-sale-auth-core`；修掉过期注释（boot 插件"全工程唯一"→ bootable 模块各自声明）；`@MapperScan` 不需改 | 单体仍自持 `/api/admin/**`、`/api/seckill/**` 鉴权（拦截器 Bean 来自 core） |
| Step 4 网关路由 | 新增精确路由（**必须排在 `/api/**` 兜底之前**）：`Path=/api/auth/**,/api/admin/users/**` → `lb://flash-sale-auth` | 先匹配先赢（YAML 顺序即匹配顺序） |
| Step 5 测试归位 | 3 个单测（`UserTokenRegistryTest` / `ApiAccessInterceptorTokenTest` / `UserAccountServiceTest`）→ `flash-sale-auth-core/src/test`；`AuthRegisterIntegrationTest` 迁到 auth app 并**删除 `/api/seckill/execute` 断言**；bootstrap 新增 `AuthEndpointsMovedTest`（`/api/auth/me`→404、`/api/admin/users`→404、`/api/admin/seckill/...` 无令牌→40100）；auth 模块新增 `src/test/resources/application.properties`（关闭 Nacos 注册） | 跨服务"登录后 execute"改由联调脚本验证，不写单测 |
| Step 6 三进程联调 | `install` → 起 8081 / 8082 / 8090 → Nacos 3 服务；经网关注册 + 登录 + `/api/auth/me` 200；直连单体 `/api/auth/login` → 404；`/api/admin/users` 带 `X-Admin-Token` 200 / 无令牌 40100；`/api/seckill/.../check` → 40100 | 见下方验收判据 |
| Step 7 回归收尾 | 停三进程 → `mvn clean test` 绿线；`plan.md` / `interface.md` 同步；4 个原子提交 | 提交信息见下 |

**验收判据**：① Nacos 服务列表含 `flash-sale-auth`（3 服务）；② 经网关 `POST /api/auth/register` → `code=0`、`GET /api/auth/me` → `code=0`；③ **单体已摘除**：`POST /api/auth/login` 直连 8081 → **404**；带**有效会话/令牌**直连 `GET /api/auth/me`、`GET /api/admin/users` → **404**（未带凭证时会先被拦截器拒绝为 `40100`，属预期，不是业务响应）；④ `/api/admin/users` 经网关带令牌 `code=0` / 无令牌 **40100**；⑤ 单体受保护接口无会话 → **40100**（非 404/500）；⑥ 全量 `mvn clean test` 绿；⑦ 前端零改动（vite 代理已指向网关 8090）。

**六个坑**：① 路由顺序（auth 必须排在 `/api/**` 之前）；② auth 服务必须扫到 `ApiAccessWebConfig`（漏了会导致 `/api/auth/me` **裸奔放行**，最危险的一种错）；③ `-pl` 运行前先 `install`（新模块 jar 不在本地仓库）；④ `ArchitectureTest` 白名单需移除已迁走的 2 个 config 类；⑤ `-Dtest=` 需带 `-Dsurefire.failIfNoSpecifiedTests=false`；⑥ 单体摘除后直连 8081 会 404（前端经网关无碍）。

**回退点**：首选 `git revert`（4 个提交可逐条回退）；手工回退 = bootstrap 依赖改回 `flash-sale-user` + 恢复该模块 + 删网关那两条路由。

**Day 2 引入的过渡期约定**：跨服务会话可用依赖"同一 `user_session` 表 + 同一 Cookie 名"；Day 4 网关统一鉴权后显式化为"网关注入身份头"。

**提交计划（4 个原子提交）**：① `refactor(p2-day2): 抽取 flash-sale-auth-core（auth 域实现库）+ 删除 flash-sale-user`；② `feat(p2-day2): flash-sale-auth 承载 auth HTTP 接口；单体收窄为仅自持鉴权`；③ `feat(p2-day2): 网关精确路由 /api/auth/** 与 /api/admin/users/** → lb://flash-sale-auth`；④ `test(p2-day2): auth 用例按模块归位 + 单体 404 断言 + 文档同步`。

**Day 2 实施记录（2026-10-14）**：

- **结构（panda 实施）**：`flash-sale-auth-core` 库建成（main 16 类：config 3 / service 2 / mapper 2 / dto 7 / web 2）；2 个 controller 迁入 `flash-sale-auth`（包 `user.controller`）；`flash-sale-user` 模块删除；父 POM 与 auth/bootstrap 两个 POM 完成依赖切换。
- **导师修正 5 处**：① 父 POM `flash-sale-auth-core` 条目重复（硬编码 `0.0.1-SNAPSHOT` vs `${project.version}`）→ 删重复；② auth/bootstrap POM 残留 `flash-sale-user` 依赖 → 改指 core；③ 2 个 controller 一度误置于 core（会导致单体仍暴露 auth 接口）→ 迁至 auth 应用；④ 3 个纯单测（`UserTokenRegistryTest` / `ApiAccessInterceptorTokenTest` / `UserAccountServiceTest`）归位 core 测试目录并修正 import；⑤ **`ApiAccessInterceptor` 被 IDE 误加 `import ...user.controller.ApiAccessWebConfig`**（移动 controller 时 Javadoc `{@link ApiAccessWebConfig}` 被解析到新包；同类同包本不需要该 import）——这是构建红根因，已删除。
- **联调（Step 2）**：网关新增精确路由（置于 `/api/**` 之前）→ 三进程 8081 / 8082 / 8090 全部就绪 → 验收结果见 Day 2 行。
- **Day 2 后演示拓扑**：`前端(5173) → 网关(8090) → { auth(8082)：/api/auth/**、/api/admin/users/** ； 单体(8081)：其余 /api/**（自持鉴权）}`。
- **经验沉淀**：① 判定"接口是否摘除"不能只看 HTTP 状态码——无处理器路径若被拦截器命中会返回 `200 + 40100`（项目业务错误约定），需**带有效凭证**再验一次才能确认真的 404；② 用 IDE 移动类后必看 diff，自动生成的 import 会把 Javadoc 引用带偏。

### P2 Day 3 详细任务卡（ai 域割接：`flash-sale-ai` 服务化）

> 执行方式：panda 实现 + 导师现场纠偏；预估 1.5~2 小时。

**目标形态**：`flash-sale-ai` 由"库"升级为**可启动服务**（端口 **8083**，服务名 `flash-sale-ai`）；AI 接口从单体摘除；网关精确导流 `/api/support/**`。

**与 Day 2 的设计差异（这次不拆"库 + 应用"）**：auth 域被单体复用（单体需要鉴权拦截器），所以拆成 `flash-sale-auth-core` 库 + `flash-sale-auth` 应用；ai 域**没有任何其它模块复用**（只是读商品/活动表拿目录数据），因此**单模块直接服务化**最省事。→ **判据：只有被多方复用的实现才需要抽库**。

**Day 3 侦察结论**：① `flash-sale-ai` 依赖 common / domain-model / infra + **product / activity**（实时目录查询），**不含 seckill / order / analytics** → 服务化后不会带入 Stream 消费者（不会误建单）；② `AiLlmProperties` 为 `@Component + @ConfigurationProperties(prefix="ai.llm")` → 组件扫描即可生效；③ `/api/support/**` **不在** `ApiAccessWebConfig` 的保护路径内（匿名接口）→ ai 服务**无需依赖 auth-core**；④ 跨模块引用 `ai.*` 仅 bootstrap 的 2 个测试类（`AiClientUnconfiguredTest`、`AiSupportChatIntegrationTest`）→ 随依赖一同迁出。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 1 服务化改造 | `flash-sale-ai` 新增启动类 `AiServiceApplication`（根包 + `@MapperScan({product.mapper, activity.mapper})`）；新增 `src/main/resources/application.yaml`（**8083** / `flash-sale-ai` / Nacos / 过渡期共用 `flash_sale` 库 / `ai.llm.*` / actuator+prometheus）；POM 补 `web`、`validation`、`mybatis-plus-starter`、`mysql`、`redis`、`redisson`、`dotenv`、`actuator`、`micrometer-registry-prometheus`、`nacos-discovery`、`starter-test(test)`，并声明 `spring-boot-maven-plugin`（`workingDirectory=聚合根`） | 该模块从此是 **bootable**（不再是纯库） |
| Step 2 单体摘除 | bootstrap POM **移除 `flash-sale-ai` 依赖** | 单体不再暴露 `/api/support/chat` |
| Step 3 网关导流 | 新增精确路由（置于 `/api/**` 之前）：`Path=/api/support/**` → `lb://flash-sale-ai` | 与 Day 2 同规则（先匹配先赢） |
| Step 4 测试归位 | `AiClientUnconfiguredTest`、`AiSupportChatIntegrationTest` 迁入 `flash-sale-ai/src/test`（包名不变）；新增 `src/test/resources/application.properties`（关闭 Nacos 注册） | 集成测试改在 ai 应用上下文中运行 |
| Step 5 四进程联调 | `install` → 起 8081 / 8082 / 8083 / 8090 → Nacos **4 服务**；经网关 `POST /api/support/chat` **空体** → `40001`（路由与参数校验生效，且**不触发真实大模型调用**）；直连单体同路径 → **404**；单体 `/api/products` 仍 200 | 用"空体 40001"验证路由，避免为验证烧 token |
| Step 6 观测栈补 job | `deploy/prometheus/prometheus.yml` 增 `flash-sale-auth`(:8082)、`flash-sale-ai`(:8083) 两个 job → `curl.exe -X POST http://127.0.0.1:9090/-/reload` | targets 应 **5/5 UP** |
| Step 7 回归收尾 | 停进程 → `mvn clean test` 绿线；`plan.md` / `interface.md` 同步；提交 | 建议 2 条原子提交 |

**验收判据**：① Nacos **4 服务**（gateway / auth / ai / monolith）；② 经网关 `/api/support/chat` 空体 → `40001`（配了 Key 时正常对话 `code=0`）；③ 直连单体 `/api/support/chat` → **404**；④ 单体其余接口（`/api/products`、`/api/seckill/...`）行为不变；⑤ 全量 `mvn clean test` 绿；⑥ Prometheus targets **5/5 UP**。

**坑**：① ai 应用必须 `@MapperScan` **product + activity** 两个包，否则启动即报 Mapper 找不到；② **不要**照抄 bootstrap 的 5 包 `@MapperScan`（会扫不存在的 seckill/order mapper）；③ ai 服务若误依赖 seckill / order，会**自动消费 Stream 并建单**——只能用现有 ai 模块的依赖集；④ 测试迁移后记得加测试侧 `application.properties` 关闭注册；⑤ 真实调用大模型会消耗额度，路由验证优先用"空体 → 40001"。

**提交计划**：① `feat(p2-day3): flash-sale-ai 服务化（:8083）+ 单体摘除 AI 域`；② `feat(p2-day3): 网关 /api/support/** → lb://flash-sale-ai + 观测栈补 2 个 job + 文档同步`。

**Day 3 实施记录（2026-10-14，导师实现）**：

- **服务化**：`flash-sale-ai` 新增根包启动类 `AiServiceApplication`（`@MapperScan(product.mapper, activity.mapper)`）+ `application.yaml`（8083 / `flash-sale-ai` / Nacos / 同库过渡 / `ai.llm.*` / actuator）+ POM 升级为 bootable（含 boot 插件 `workingDirectory=聚合根`）。
- **单体摘除 + 网关导流**：bootstrap 移除 `flash-sale-ai` 依赖；网关新增 `Path=/api/support/**` → `lb://flash-sale-ai`（置于兜底前）。
- **测试归位与边界守卫**：`AiClientUnconfiguredTest` / `AiSupportChatIntegrationTest` → ai 模块；**`AuthRegisterIntegrationTest` → auth 模块**（Day 2 遗留：它仍在 bootstrap 打单体 auth 接口 → 全红），并把原先依赖 `/api/seckill/execute` 的 CSRF 断言改写为 `POST /api/auth/logout`（缺 `X-CSRF-Token` → `40301`；带则 `code=0`；退出后 `me` → `40100`）；新增 bootstrap 守卫测试 `DomainEndpointsMovedTest`（POST `/api/auth/register|login` 与 `/api/support/chat` → **404**）；R9 架构规则随 ai 类迁入 ai 模块（`AiArchitectureTest`）。
- **关键约束（本轮最大教训）**：**bootable 模块不能被其它模块依赖** —— 声明 boot 插件后 `install` 产出可执行 jar（类在 `BOOT-INF/classes`），bootstrap 编译期取不到 ai 的包而构建红。故「服务化」必须与「单体摘除 + 测试归位」**同批**（与 Day 2「摘除与导流同批」同源）。
- **首轮回归 6 失败 → 三处修复**：① `ArchitectureTest.R9_ai只允许依赖商品与活动` 因 bootstrap 不再含 `..ai..` 类而报 "failed to check any classes" → 规则迁到 ai 模块；② `AuthRegisterIntegrationTest` 5/5（Day 2 遗留）→ 迁 auth 模块并改写断言；③ 新增守卫测试的 `POST /api/auth/logout` 断言口径错误（该路径被拦截器覆盖 → 200 + 非 0 业务码，而非 404）。
- **Day 3 后演示拓扑**：`前端(5173) → 网关(8090) → { auth(8082)：/api/auth/**、/api/admin/users/**； ai(8083)：/api/support/**； 单体(8081)：其余 /api/**（自持鉴权）}`；Prometheus 抓取 5 个目标。
- **经验沉淀**：① **架构规则要与它约束的类同模块**（否则类被抽走后规则会因"选不到类"而失效）；② 判定"接口是否还在"用 **POST**（GET 在无处理器路径上会被拦截器提前拒绝，返回 `200 + 40100`，无法区分"接口没了"与"没登录"）；③ 跨服务链路的断言不要塞进单模块的集成测试（本模块内测 HTTP 行为，跨服务走联调脚本）。

### P2 Day 4 详细任务卡（网关统一鉴权 · P2 收口）

> 执行方式：导师实现 + panda 审查；预估 2~3 小时。

**目标**：把「**准入鉴权**」下沉到网关（管理令牌 + 用户态凭证），服务侧拦截器**保留为纵深防御**；P2 收口并打 `p2-done` 标签。

**设计要点（分层鉴权，而非一步到位）**：

- **网关做准入**：`/api/admin/**` 校验 `X-Admin-Token`；用户态路径要求携带会话 Cookie（兼容 `X-User-Token`）；不通过由**网关直接返回** `40100` 信封（**HTTP 200 + body code**，与项目既有约定一致，前端零改动）。
- **服务侧做细校验**：会话有效性、CSRF、资源归属仍在服务（拦截器不撤）→ **直连服务依旧受保护**，避免"绕过网关即裸奔"。
- **不做**：网关校验会话有效性需跨服务 introspection 或 JWT 无状态化 → 属 P3+ 演进（记入 `microservices-plan.md` 后续项）。

| 步骤 | 内容 |
|---|---|
| Step 1 POM/配置 | 网关 POM 补 `flash-sale-common`（复用 `ResultCode` / `RequestIdUtil`）、`spring-dotenv`（读 .env 管理令牌）、`spring-boot-starter-test`；boot 插件补 `workingDirectory=聚合根`；yaml 增 `security.admin-token: ${ADMIN_TOKEN:}` 与 `gateway.auth.session-cookie-name` |
| Step 2 准入过滤器 | 新增 `AuthGatewayFilter`（`GlobalFilter`，order = `HIGHEST_PRECEDENCE + 10`，紧随 traceId）：OPTIONS 放行 → `/api/admin/**` 全量校验管理令牌 → 用户态路径校验凭证存在性 → 否则 40100 JSON |
| Step 3 单测 | `AuthGatewayFilterTest`（`MockServerWebExchange` 纯单测，8 例：预检放行 / admin 无令牌 / admin 令牌错 / admin 令牌对 / 令牌未配置 / 用户路径无凭证 / 用户路径带 Cookie / 公开路径） |
| Step 4 联调验收 | 见下方判据表（含"直连服务仍被拒"的纵深防御验证与 traceId 贯通） |
| Step 5 收口 | 全量回归绿线；`plan.md` Day 4 ✅；`microservices-plan.md` 补 P2 小结；`git tag p2-done` |

**验收判据**：① 经网关 `/api/admin/seckill/activities` **无令牌** → `40100`（**由网关返回**，网关日志可辨）；② **直连单体 8081** 同路径无令牌 → `40100`（服务侧纵深防御仍在）；③ 经网关带 `X-Admin-Token` → 正常业务响应；④ 经网关 `POST /api/seckill/execute` 无会话无令牌 → `40100`（网关拒绝）；⑤ 带有效会话经网关 execute 但**缺 CSRF** → `40301`（服务侧细校验生效，证明网关只是准入）；⑥ 经网关 `/api/products`（公开路径）→ 200 不受影响；⑦ `X-Trace-Id` 仍**单值**贯通（拒绝响应也带）；⑧ 网关单测全绿 + 全量回归绿线。

**坑**：① 网关读不到 `.env` → 管理令牌为空 → **所有 admin 请求被判 40100「未配置」**（必须给 boot 插件配 `workingDirectory`）；② 40100 响应体形态必须与单体一致（HTTP 200 + `code`），否则前端 `http.ts` 走错分支；③ **OPTIONS 预检必须放行**，否则浏览器 CORS 全挂；④ 过滤器顺序须在 traceId 之后，保证拒绝响应也带 traceId 便于排查；⑤ 服务侧拦截器**不要撤**（P3 起才考虑按服务逐步收敛）。

**Day 4 实施记录（2026-10-14，导师实现）**：

- **准入过滤器**：新增 `AuthGatewayFilter`（`GlobalFilter`，order = `HIGHEST_PRECEDENCE + 10`，紧随 traceId）：OPTIONS 放行 → `/api/admin/**` 全量校验管理令牌 → 用户态路径（`/api/seckill/execute`、`/api/seckill/activities/*/check`、`/api/seckill/users/*/orders`、`/api/seckill/orders/*`、`/api/auth/me|csrf|logout`）校验凭证存在性（会话 Cookie 或兼容 `X-User-Token`）→ 拒绝时由网关直接返回 **HTTP 200 + `code=40100` 信封**（形态与单体一致，前端零改动）。
- **网关工程补齐**：POM 加 `flash-sale-common`（复用 `ResultCode` / `RequestIdUtil`）、`spring-dotenv`（读 `.env` 的管理令牌）、`spring-boot-starter-test`；boot 插件补 `workingDirectory=聚合根`（否则读不到 `.env` → admin 全被判「未配置」）；yaml 增 `security.admin-token` 与 `gateway.auth.session-cookie-name`。
- **单测**：`AuthGatewayFilterTest` 9 例（预检放行 / admin 无令牌 / 错令牌 / 对令牌 / 未配置 / 用户路径无凭证 / 带 Cookie / 带 `X-User-Token` / 公开路径）全绿。
- **联调**：见 Day 4 行；网关日志可见 `gw 准入拒绝 <method> <path> -> 40100 <msg>` 作为"确实由网关拦下"的证据。
- **踩坑与教训（重要）**：① **不要在服务运行期间执行 `mvn install`** —— Maven 会重写 `~/.m2` 中**运行中 JVM 正在读取**的模块 jar，表现为"随机崩溃"（本轮实测：单体 `EOFException: Unexpected end of ZLIB input stream`、auth 服务 `ClassNotFoundException: IdGenerator`）；正确顺序是 **停服务 → 构建/安装 → 再启动**。② 验收脚本必须打在**真实存在**的端点上：`GET /api/admin/seckill/activities` 并不存在（该路径只有 POST 创建），用它验收会得到 `50000`；顺带发现可选优化项——访问不存在的 `/api/admin/**` 路径返回 `50000` 而非 `404`。

---

## 九、第 5 阶段 · P3：商品与活动域独立（逐日进度）

**P3 逐日进度（商品与活动域独立）：**

| 日 | 任务 | 状态 | 验证 / 提交 |
|---|---|---|---|
| Day 1 | product 域「**库 + 应用**」拆分：`flash-sale-product` 改名 `flash-sale-product-core`，新建 `flash-sale-product` 应用（:8084）并注册 Nacos；**零行为变更** | ✅ 2026-10-15 | `install` exit=0（16 模块）；`:8084` health **UP**（db/redis/nacosDiscovery 全 UP）；Nacos **5 服务**（新增 `flash-sale-product`）；**零行为变更**：网关路由表本日无 product 路由、经网关 `/api/products` 200、`/api/admin/products/1` 带令牌 `code=0` / 无令牌 `40100`（网关准入）、`X-Trace-Id` 单值、**停 8084 后经网关 `/api/products` 仍 200**（证明仍由单体服务）；全量回归 **124 用例 0 失败 BUILD SUCCESS**（1.6 分钟，16 模块全 SUCCESS）；观测栈补 `flash-sale-product` job（Prometheus targets **6/6 UP**）；改名提交 `3afc88b`、应用与登记提交 `fa00c89` |
| Day 2 | product 域割接：controllers + FileStorageWebConfig 迁入应用；单体摘除；网关导流 `/api/products/**`、`/api/admin/products/**`、`/api/admin/files/**`、`/uploads/**` | ✅ 2026-10-15 | 接口迁出：3 controller + `FileStorageWebConfig` 迁入应用（git 识别 **4 个 rename**），core jar 内已无 `product/controller|config` → 单体自动摘除（**未改任何 POM**）；网关导流（**含 `/uploads/**`**，置于 `/api/**` 兜底前）：经网关 `/api/products` **200**、`/api/products/1` 200、**直连单体 404**、直连 8084 200；admin 带令牌 `code=0` / 无令牌 `40100`（网关准入）；**上传→回读**：`POST /api/admin/files/image` `code=0`（storageType=local）→ 经网关 `GET /uploads/...` **200**、直连单体 **404**；**反证：停 8084 后经网关 503**（网关日志 `No servers available for service: flash-sale-product`；Day 1 同测为 200）；其它域不变（`/api/health` 200、`auth/me` 40100、`support/chat` 40001）、`X-Trace-Id` 单值；全量回归 **125 用例 0 失败 BUILD SUCCESS**（2.2 分钟，16 模块） |
| Day 3 | 数据所有权：product / activity 独立 schema（DDL + 迁移脚本）+ 双写开关 + 对账脚本 | ✅ 2026-10-15 | 新库 `flash_sale_product` / `flash_sale_activity` 建成：列定义/索引与旧库**逐项一致**（10 列 / 13 列、索引 3/3 与 4/4），**外键归零**（旧库 5 个 FK 含 2 个跨域 FK，新库 0 个）；幂等迁移（连跑 3 次 exit=0）；**双写镜像**：开 → 经网关建商品/活动（含更新、预热）后新库逐字段一致（`preheat=1 ver=1`）、日志 `target=product 1 次 / target=activity 4 次`；**关 → 新库完全不动**（主库 6→7 / 20→21，新库仍 6/20）；**对账脚本**正/负向双向验证（8/8 OK exit=0；人为改一行 → DIFF exit=1；重跑迁移重同步 → 恢复 OK）；全量回归 **125 用例 0 失败 BUILD SUCCESS**（1.9 分钟，16 模块）；读路径未变 → **零行为变更** |
| Day 4 | activity 域服务化（:8085，同「库 + 应用」模式）+ 网关导流 + 预热锁归属 activity | ✅ 2026-10-15 | 库改名 `flash-sale-activity-core`（3 处引用同步）+ 新应用 `flash-sale-activity`（reactor **17 模块**、Nacos **6 服务**、`:8085` health db/redis/nacos 全 UP）；控制器一分为二（activity 三接口进应用、`dead-letters/replay` 留单体，**路径全不变**）；网关**精确三条 Path**：经网关建/改活动 `code=0`、预热 `code=0`（stock=50）、重置库存 `code=0`（stock=60）；**metrics `code=0` 与 replay `40004` 仍由单体**；**反证：停 :8085 → 三条 activity 路径 503**（日志 `No servers available for service: flash-sale-activity`）而 metrics 仍 200；直连单体 **404** / 直连 :8085 200；**双写回归** 22/22 逐字段一致 + 对账 **8/8 consistent**；全量回归 **126 用例 0 失败 BUILD SUCCESS**（1.8 分钟） |
| Day 5 | seckill 提供内部「库存初始化/重置」接口（供 activity 编排）+ 全量回归 + 收口 | ✅ 2026-10-15 | **库存权属**：预热的库存写入由单体 seckill 域完成（日志 `内部接口写入活动库存完成` ×2 + `库存写入成功` ×2，activity 侧 **0 次**）；**内部接口护栏**：无令牌/错令牌 → `40100`（fail-closed），带令牌才通过；**纵深防御**：直连 :8084/:8085 的 `/api/admin/**` 无令牌 → **40100**、带令牌 → 业务码；**去跨域直读**：活动创建校验改调商品服务内部接口（停 :8084 → 建活动 `50000` 且**库里零写入**，恢复后 `code=0`），活动广场 22 条带商品名/图；双写 22/22 + 对账 **8/8**；全量回归 **129 用例 0 失败 BUILD SUCCESS**（1.8 分钟） |

### P3 Day 1 详细任务卡（product 域「库 + 应用」拆分，零行为变更）

> 执行方式：导师实现 + panda 审查；预估 2~3 小时。

**目标**：把 `flash-sale-product` 一分为二 —— `flash-sale-product-core`（库：mapper / service / cache / storage / dto / vo）+ `flash-sale-product`（**应用**：启动类 + 配置，:8084，注册 Nacos）。**Day 1 不动任何控制器、不改网关路由** → 单体与现网行为零变化。

**为什么必须"库 + 应用"（复用 P2 的教训）**：`flash-sale-activity`（活动 VO 需要商品信息）与 `flash-sale-ai`（客服目录需要商品表）**都依赖 product 的实现**；一旦某模块声明 boot 插件，其产物是可执行 jar（类在 `BOOT-INF/classes`），**无法再被依赖**。因此必须保留一个 core 库给它们，应用只承载"对外接口 + 启动"。

**Day 1 侦察结论**：

- `flash-sale-product` 共 14 文件：controller 3（`ProductController` / `AdminProductController` / `AdminFileController`）、cache 1（`ProductCacheService`）、config 1（`FileStorageWebConfig`）、mapper 1 + `ProductMapper.xml`、service 2、storage 4（本地/OSS 双策略）、dto 1、vo 1。
- 依赖 product 的模块：**bootstrap**、**flash-sale-activity**、**flash-sale-ai**（+ 父 POM dependencyManagement）。
- 存储与缓存归属：`product:detail:*` 缓存键、`uploads/` 本地目录、OSS 凭据（`aliyun.oss.*`）均属 product 域 —— P3 的"缓存键所有权 + OSS 凭据"由本域收拢。
- DB 脚本现状：`flash-sale-bootstrap/src/main/resources/db/schema.sql` + `db/migration/V20261010__user_accounts_and_sessions.sql`（Flyway 命名风格、当前手工执行）——Day 3 独立 schema 将沿用该目录约定。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 1 库改名 | `git mv flash-sale-product flash-sale-product-core`；改 `artifactId` / `name` / `description`；父 POM 的 `<module>` 与 `dependencyManagement` 同步；**bootstrap / activity / ai 三处依赖的 artifactId 改为 `flash-sale-product-core`** | 整模块改名，零文件级搬迁，风险最低 |
| Step 2 新建应用 | 新建 `flash-sale-product`：POM（依赖 `flash-sale-product-core` + web/validation/mybatis-plus/mysql/redis/redisson/dotenv/actuator/micrometer/nacos-discovery + boot 插件 `workingDirectory=聚合根`）、根包启动类 `ProductServiceApplication`（`@MapperScan("com.ghb.ecommerceflashsalesystem.product.mapper")`）、`application.yaml`（**8084** / 服务名 `flash-sale-product` / Nacos / 过渡期共用 `flash_sale` 库 / `aliyun.oss.*` 与 `storage.local.dir` / actuator） | 与 `flash-sale-auth` / `flash-sale-ai` 同款形态 |
| Step 3 编译与启动 | `mvnw -DskipTests install` → 起 8084（**期间不要重启其它服务**）→ `/actuator/health` UP、Nacos **5 服务** | 注意 Day 4 教训：**服务运行期间不要跑 `mvn install`**（会重写 `~/.m2` 中被 JVM 读取的 jar） |
| Step 4 零行为变更验证 | 经网关 `/api/products` 与 `/api/admin/products/1`（带管理令牌）仍走单体、行为不变；单体 `/api/products` 仍 200 | 网关路由本日**不改** |
| Step 5 回归与提交 | 停四/五服务 → `mvnw clean test` 绿线；提交 1 条原子提交；`plan.md` P3 Day 1 行 → ✅ | 回退点：停 8084 进程或 `git revert` |

**验收判据**：① `install` exit=0；② 应用 `:8084/actuator/health` = UP；③ Nacos 服务数 **5**（gateway / monolith / auth / ai / product）；④ 经网关 `/api/products?page=1&pageSize=3` → 200 且**仍由单体服务**（行为不变）；⑤ 单体 `/api/products/1` → 200；⑥ 全量 `mvn clean test` 绿线（用例数应仍为 124）；⑦ 工作区可一条提交回退。

**坑**：① 改名后**遗漏任何一处 artifactId 引用**都会编译失败（父 POM modules、dependencyManagement、bootstrap/activity/ai 三处依赖）；② 新应用 `@MapperScan` 必须包含 `product.mapper`（否则启动即报 Mapper 缺失）；③ 应用启动会占用 8084，且 `aliyun.oss.*` / `storage.local.dir` 配置必须与单体一致（否则上传路径不一致）；④ **不要**顺手把 controllers 移到应用里——那是 Day 2 的原子动作（与网关导流同批，避免破窗态）；⑤ 运行期不要执行 `mvn install`（Day 4 已踩过）。

### P3 Day 2 详细任务卡（product 域割接：接口迁出 + 网关导流）

> 执行方式：panda 实现 + 导师现场纠偏；预估 1.5~2 小时。

**目标**：3 个 controller + `FileStorageWebConfig` 迁入 `flash-sale-product` 应用（:8084）；单体不再暴露商品/上传接口；网关把 4 组路径导流到 `lb://flash-sale-product`。**摘除与导流同批提交**（避免"接口已摘除但未导流"的破窗态）。

**Day 2 侦察结论（决定动作面）**：

- **待迁文件 4 个**：`ProductController`（`GET /api/products`、`GET /api/products/{productId}`）、`AdminProductController`（`GET /api/admin/products/{productId}`、`POST /api/admin/products`）、`AdminFileController`（`POST /api/admin/files/image`，multipart 字段名 `file`）、`FileStorageWebConfig`（`/uploads/**` → `{storage.local.dir}` 静态映射）。包名 `product.controller` / `product.config` **不变** → **无需改任何 import**。
- **单体摘除不需要改 POM**：接口类本就在 `flash-sale-product-core` 里，迁出后单体（依赖 core）自动不再暴露接口。`activity` / `ai` 依赖的是 core 的 **service / mapper / VO**，**不受影响 —— 这些绝不要搬**。
- **存储 Bean 的选择方式**：`@ConditionalOnProperty(name="aliyun.oss.enabled")` → `false`/缺省用 `LocalImageStorageService`，`true` 用 `OssImageStorageService`，**同一时刻仅一个 Bean**（应用 yaml 已有该配置）。
- **没有测试用 MockMvc 打商品/上传 HTTP 接口**（6 个测试引用商品域类，但都是直接注入 service/storage Bean）→ 迁接口不会打断现有测试；只需**新增"单体侧接口归属"守卫断言**。
- `/uploads/**` **不在** `/api/**` 下 → 必须单独写进路由的 path 列表；它是公开静态资源（网关 `AuthGatewayFilter` 只拦 `/api/admin/**` 与用户态路径，行为与现状一致）。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 前置 | 停 5 服务（8081/8082/8083/8084/8090）；工作区 clean | **停服务再构建**（Day 4 教训：运行期 `install` 会重写 `~/.m2` 中被运行中 JVM 读取的 jar） |
| Step 1 迁接口 | `git mv` 4 文件：`flash-sale-product-core/.../product/controller/{ProductController,AdminProductController,AdminFileController}.java` → `flash-sale-product/src/main/java/com/ghb/ecommerceflashsalesystem/product/controller/`；`.../product/config/FileStorageWebConfig.java` → `.../product/config/` | 包名不变；**不要**搬 service / mapper / cache / storage / dto / vo |
| Step 2 网关导流 | 在 auth / ai 路由之后、单体兜底之前插入：`- id: flash-sale-product` / `uri: lb://flash-sale-product` / `predicates: - Path=/api/products/**,/api/admin/products/**,/api/admin/files/**,/uploads/**` | **`/uploads/**` 必写**（图片是独立前缀，最容易漏）；先匹配先赢 |
| Step 3 守卫测试 | 扩展 `flash-sale-bootstrap/src/test/.../integration/DomainEndpointsMovedTest.java`：新增 `GET /api/products`、`GET /api/products/1` → **404**；`GET /uploads/probe.jpg` → **404** | 不要用 `/api/admin/**` 断言 404（admin 路径先被拦截器覆盖 → 返回 `200 + 40100`） |
| Step 4 构建与联调 | 停服务状态下 `mvnw -DskipTests install` → 起 5 服务 → 按下方判据表验收 | 起服务前确认 Nacos 在线（否则 `NacosException: Client not connected` 秒退） |
| Step 5 回归收尾 | 停 5 服务 → `mvnw clean test` 绿线 → `plan.md` Day 2 → ✅ → 提交（建议 2 条原子提交） | 回退点：`git revert`，或把网关路由回退 + 还原 4 个文件 |

**验收判据**：① `install` exit=0；② Nacos 仍 **5 服务**；③ 经网关 `GET /api/products?page=1&size=3` → 200，**且停掉 8084 后经网关变为 503**（证明路由已真正切到新服务 —— 与 Day 1 的"停 8084 仍 200"正好相反）；④ **直连单体** `GET /api/products` → **404**（接口已摘除）；⑤ 直连 8084 同路径 → 200；⑥ 经网关 `GET /api/admin/products/1`：带 `X-Admin-Token` → `code=0`、无令牌 → `40100`（网关准入）；⑦ **上传链路**：经网关 `POST /api/admin/files/image`（带令牌 + multipart `file`）→ `code=0` 且 `data.url` 有值，再 `GET {url}` 经网关 → 200（本地策略下 url 形如 `/uploads/yyyyMM/uuid.ext`）；⑧ 其它域不受影响：`/api/seckill/activities` 正常、`/api/support/chat` 空体 `40001`、`/api/auth/me` 无会话 `40100`；⑨ `X-Trace-Id` 单值；⑩ 全量回归绿线（**124 + 新增守卫断言**，用例数可能略增）。

**坑**：① 路由顺序（product 必须在 `/api/**` 兜底之前）；② **漏写 `/uploads/**`** → 图片在网关侧 404（本地存储策略下最易漏）；③ `FileStorageWebConfig` 的路径基于**应用运行目录** → 必须确保应用 boot 插件 `workingDirectory=聚合根`（Day 1 已配），否则 `uploads/` 解析到别处导致图片 404；④ 别顺手搬 storage / service（activity、ai 依赖 core）；⑤ `GET /api/admin/**` 不能用 404 做判据（拦截器先拒）；⑥ 停服务再构建。

**提交计划**：① `refactor(p3-day2): product 接口（3 controller + FileStorageWebConfig）迁入 flash-sale-product 应用 + 网关导流（摘除与导流同批）`；② `test(p3-day2)+docs: 单体商品接口归属守卫断言 + plan.md Day 2 记录`。

**Day 2 实施记录（2026-10-15）**：

- **接口迁出**：`ProductController` / `AdminProductController` / `AdminFileController` / `FileStorageWebConfig` 自 `flash-sale-product-core` 迁入 `flash-sale-product` 应用（`git mv`，包名不变 → **零 import 改动**；侦察确认全仓无外部引用这 4 个类）。
- **单体摘除无需改 POM**：接口类原本就在 core 库中，迁出后单体（依赖 core）**自动**不再暴露商品/上传接口 —— 与 P2（整模块摘除 + 改 bootstrap 依赖）不同，这里靠"接口搬迁"完成摘除；`activity` / `ai` 依赖的是 core 的 service/mapper/VO，不受影响。
- **网关导流**：新增 `id: flash-sale-product` → `Path=/api/products/**,/api/admin/products/**,/api/admin/files/**,/uploads/**`（置于 `/api/**` 兜底之前）。**`/uploads/**` 必写**：它不在 `/api/**` 前缀下，兜底路由永远匹配不到；漏写则图片经网关 404（本次首版即漏写，验收前补上）。
- **守卫测试判据依据**：`/api/products/**` 属**公开路径**（不在 `ApiAccessWebConfig` 保护列表内）→ 无处理器时 GET/POST 都为 404，判据干净；而 `/api/admin/**` 会被拦截器**先拒**（`200 + 40100`），**不能**用于判"接口是否搬走"。
- **踩坑与观察**：① 验收脚本把上传接口返回的**绝对 URL** 直接拼在网关地址后 → `curl` 得 `000`（URL 非法）；回读应使用"**相对路径 + 网关**"或"直接用返回的绝对 URL"；② 停掉 8084 后**第一次**请求网关返回 **500**（LB 缓存里仍有死实例、连接被拒），约 35s 后（Nacos 摘除实例 + LB 缓存过期）变为 **503** 且日志出现 `No servers available for service: flash-sale-product` → 判据以"**5xx 且网关日志指向该服务**"为准更稳；③ `LocalImageStorageService` 的 URL 由**当前请求**的 scheme/host/port 拼接，网关路由会改写 Host → 上传返回 `http://<host>:8084/uploads/...`（与割接前 `:8081` 同构，**非回归**；后续可考虑改为相对路径或可配置公开域名）；④ `uploads/` 目录基于**应用运行目录** → 依赖 boot 插件 `workingDirectory=聚合根`（Day 1 已配），因此单体与应用共用同一个图片目录，**无需迁移历史文件**；⑤ 服务列表 API（`/v1/ns/service/list`）列出的是**服务名**而非**实例**：8084 停掉后仍显示 `flash-sale-product`（count=5），但实例已摘除（LB 报 no servers）——查实例要用 `/v1/ns/instance/list?serviceName=...`。

### P3 Day 3 详细任务卡（数据所有权：product / activity 独立 schema + 双写开关 + 对账脚本）

> 执行方式：panda 实现 + 导师现场纠偏；预估 2~3 小时。

**目标**：为 product 与 activity 两个域建立**独立 schema**（DDL + **幂等**迁移脚本），写路径加**双写开关**（默认关），并提供**对账脚本**。**本日不切换读路径**（读仍走 `flash_sale`）→ 行为零变化。

**Day 3 侦察结论**：

- 现状：单库 `flash_sale`（utf8mb4/unicode_ci）、**8 张表**；monolith / auth / ai / product 四个应用**全部连同一个 URL** `jdbc:mysql://localhost:3306/flash_sale`。
- 表 → 域归属：`product` → product；`seckill_activity`、`seckill_activity_snapshot` → activity / analytics；`seckill_order`、`seckill_message_log` → seckill/order；`user_account`、`user_session` → auth。
- **存在跨域外键（本日最关键的约束）**：`seckill_activity.fk_activity_product → product(id)`、`seckill_order.fk_order_product → product(id)`、`seckill_order.fk_order_activity → seckill_activity(id)`、`seckill_activity_snapshot.fk_snapshot_activity → seckill_activity(id)`。
- mapper 现状：`ProductMapper`(+XML)、`SeckillActivityMapper`(+XML)、`SeckillOrderMapper`(+XML)、`UserAccountMapper`/`UserSessionMapper`、`SeckillActivitySnapshotMapper`。
- 脚本约定：`flash-sale-bootstrap/src/main/resources/db/{schema.sql, migration/V*__*.sql}`，**手工执行**（未接 Flyway）；本机 `mysql.exe` 在 `D:\MySQL\mysql-8.0.34-winx64\bin`。
- 缓存/锁键归属：`product:detail:{id}`（product）、`seckill:stock:{activityId}`（activity 实时库存）、`preheat_status` 列（activity）。

**表归属裁定（本日决策）**：

| 表 | 归属域 | 本日动作 |
|---|---|---|
| `product` | product | → 新库 `flash_sale_product` |
| `seckill_activity` | activity | → 新库 `flash_sale_activity` |
| `seckill_activity_snapshot` | analytics（读模型） | **暂留** `flash_sale`（收口日再定） |
| `seckill_order` / `seckill_message_log` | seckill / order | 暂留（P4 拆） |
| `user_account` / `user_session` | auth | 暂留（auth schema 迁移列为后续项） |

**关键设计取舍（先想清楚再动手）**：

- **跨域外键必须去掉**：MySQL 的外键约束只在**同一个 schema 内**维护，域拆分后"活动→商品""订单→商品/活动"的约束自然消失 → 完整性改由**应用层校验**保证（创建活动时校验商品存在，项目里已经在做）。
- **用 `CREATE TABLE ... LIKE` 复制结构**：它复制列/索引/自增但**不复制外键** —— 正好符合"拆域去 FK"的目的，且避免手抄 DDL 产生结构漂移。
- **双写是过渡期方案**：主库写入成功后，把同一条变更**镜像**到新 schema；镜像失败**不影响主流程**（try/catch + 日志 + Micrometer 计数）。
- **本日不切读**：切换读路径（改 datasource / 用视图兼容）留到收口日，保证零行为变更。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 前置 | 停 5 服务；记录基线（`COUNT(*)`、`SUM(total_stock)`、`SUM(seckill_stock)`）；确认 `mysql.exe` 可用 | **停服务再做 DDL/迁移**（连接池/缓存会不一致） |
| Step 1 新 schema DDL | 新增 `db/schema-product.sql`、`db/schema-activity.sql`：`CREATE DATABASE IF NOT EXISTS flash_sale_product \| flash_sale_activity`（utf8mb4/utf8mb4_unicode_ci）+ `CREATE TABLE ... LIKE flash_sale.product \| seckill_activity` | 字符集/排序规则与旧库一致；**不复制外键**（正是目的） |
| Step 2 幂等迁移脚本 | 新增 `db/migration/V20261015__split_product_activity_schema.sql`：建库建表 + `INSERT ... SELECT ... ON DUPLICATE KEY UPDATE`（或 `REPLACE INTO`）+ 行数校验查询 | **可重跑**（幂等）；脚本尾部写回滚说明 |
| Step 3 双写开关（默认关） | `flash-sale-infra` 新增 `DualWriteMirror`（第二数据源 `module.db.dual-write.*`，Hikari 小连接池）+ 属性类；接入写路径：`ProductServiceImpl.saveProduct`、`SeckillActivityServiceImpl` 创建/更新、`SeckillCacheService` 的 `preheat_status` 更新 | **主库提交后**再镜像；异常只记日志 + `dual_write_failures_total` 指标；开关默认 `false` |
| Step 4 对账脚本 | 新增 `scripts/reconcile-product-activity.ps1`：读 `.env` 的 `MYSQL_PASSWORD`，用 `mysql.exe` 比对**行数 / MAX(updated_at) / 关键聚合**（`SUM(total_stock)`、`SUM(seckill_stock)`），输出差异明细并给退出码 | 有差异 → 退出码非 0（可作为切换前置门禁） |
| Step 5 验证 + 回归 + 文档 + 提交 | 双写开→建商品/改活动→新库可见；双写关→新库不动；对账 `OK`；全量回归绿线；plan.md Day 3 → ✅；1~2 条原子提交 | 读路径未变 → 行为零变化 |

**验收判据**：① 两个新库存在，`SHOW CREATE TABLE` 与旧库**仅差外键部分**；② 迁移后行数一致（`product`、`seckill_activity` 与迁移前相同）；③ **双写开**：经网关 `POST /api/admin/products` 建商品 → `flash_sale_product.product` 可见新行；活动更新/预热 → `flash_sale_activity.seckill_activity.preheat_status` 同步变化；④ **双写关**：新库无新行（旧库照常）；⑤ 对账脚本输出 `OK`（退出码 0），人为改一行后输出 diff（退出码 1）——证明脚本真能发现问题；⑥ 全量回归绿线（**读路径未变** → 行为零变化）；⑦ 代码/脚本/文档可一条提交回退。

**坑**：① **跨域外键在拆分后不复存在** —— 不要在新 schema 里重建 `fk_activity_product`（MySQL 外键不能跨库维护），完整性交应用层；② `CREATE TABLE ... LIKE` 复制索引但**不带外键**（符合预期，须在文档写明）；③ 迁移脚本必须**幂等**，否则重跑报主键冲突；④ 双写要"**先主库提交、后镜像**"，镜像失败不回滚主库，且**不要放进主库事务**；⑤ **别在服务运行期做 DDL/迁移**；⑥ 双写开关默认 **false**，避免准备不足就写脏数据；⑦ 本日**不动读路径**，切换留到收口日。

**提交计划**：① `feat(p3-day3): product/activity 独立 schema DDL + 幂等迁移脚本 + 对账脚本`；② `feat(p3-day3): 双写开关（默认关）+ infra 镜像组件 + 写路径接入 + 文档`。

**Day 3 实施记录（2026-10-15）**：

- **备份先行**：`mysqldump --single-transaction --databases flash_sale` → `D:\Project\backup\flash_sale-before-p3d3.sql`（1545 KB）；基线 `product 6 行 / total_stock=1439`、`seckill_activity 20 行 / seckill_stock=7383`。
- **`CREATE TABLE ... LIKE` 的价值**：结构复制零漂移（列/索引逐项一致比对通过），且**天然不复制外键** —— 跨域外键（`fk_activity_product → product`、`fk_order_product`、`fk_order_activity`、`fk_snapshot_activity`）的"域内归属"就此理清：新库 0 个外键，完整性交应用层（创建活动校验商品存在、下单校验活动与商品，代码里已在做）。
- **幂等迁移**：`REPLACE INTO ... SELECT *` + 脚本尾部行数校验；重复执行结果一致（本次连跑 3 次 exit=0）。它同时承担"**全量重同步**"职责 —— 双写关闭期间产生的差量，靠重跑该脚本补平（本次验证期实测走过这条路：OFF 周期的两条数据就是靠重同步补进新库的）。
- **双写镜像（`infra.db.DualWriteMirror`）四条约束**：① 默认关；② 只镜像写、不动读；③ 失败只记日志 + 计数（主库已提交、不回滚）；④ 调用点在**主库事务提交之后**（不在事务内）。刻意**不依赖任何业务实体**（infra 不能反向依赖业务模块），由调用点传 SQL + 参数 → "到底镜像了什么"在调用点一目了然。
- **接入点 4 处**：`ProductServiceImpl.saveProduct`（insert / update 两分支，整行 UPSERT）；`SeckillActivityServiceImpl.createActivity`（insert / update 两分支，整行 UPSERT）；`SeckillCacheService.doPreheat`（成功 / 失败各一次）与 `resetPreheatStatusToUnpreheated` —— 后两者是**列级** `UPDATE ... SET preheat_status=?`，避免整行覆盖把并发下的 version / 秒杀库存写回旧值。
- **踩坑与修正（4 条，都值钱）**：① `limitPerUser` **只允许 1**（一人一单由订单唯一键硬约束）—— 验证脚本传 2 被 `40001` 拒；② 对账脚本被 PowerShell 5.1 按 **GBK** 读 → 中文乱码破坏引号解析 → `.ps1` 必须存成 **UTF-8 with BOM**；③ PowerShell 向原生 exe 传参会**吃掉双引号** → SQL 里不能用 `"-"`，改用 `CHAR(45)`；④ 对账项弃用 `MAX(updated_at)`（主库写入与镜像可能跨秒 → 假阳性），改用**业务列内容指纹**（MD5 over ordered concat，剔除时间列）。
- **测试适配**：`CreateBusinessValidationTest` / `AdminSeckillResetStockTest` / `SeckillCachePreheatRepairTest` 三处手工 `new` 服务的用例，注入"关闭态"镜像（`new DualWriteMirror(new DualWriteProperties())`），单测语义不变。
- **运行期证据（双写开）**：新建商品 `P3D3-ON-ProductB`、新建 + 更新活动 `P3D3-ON-ActB-v2`、预热成功 → 新库 `8/22` 与主库**逐字段一致**（`price=1400 stock=28 preheat=1 ver=1`）；应用日志 `target=product 1 次`（product 服务）、`target=activity 4 次`（单体：创建 / 更新 / 回置 / 预热）。验证后**已清理探针数据**（两个库同时删，清理后对账仍 8/8 OK，行数回到 6/20）。
- **回滚方式**：`DROP DATABASE flash_sale_product; DROP DATABASE flash_sale_activity;`（读路径未切换 → 业务无感）；代码侧 `git revert` 本日提交。

---

### P3 Day 4 详细任务卡（activity 域服务化：库 + 应用 :8085 + 网关精确导流）

> 执行方式：panda 实现 + 导师现场纠偏；预估 2~3 小时。

**目标**：`flash-sale-activity`（库）改名 `flash-sale-activity-core`；新建 `flash-sale-activity` 应用（:8085）；**只把 activity 自己的管理端接口**（建/改活动、预热、重置库存）迁入应用并**精确导流**；`dead-letters/replay`（order 域）留在单体；预热锁随库进应用；**读路径仍指旧库**（Day 5 切换）。

**Day 4 侦察结论（决定动作面）**：

- `AdminSeckillController` 在 **bootstrap**（`controller.admin` 包），**混了 3 个域**：`POST /activities`、`POST /activities/{id}/preheat`、`POST /activities/{id}/reset-stock`（activity）与 **`POST /dead-letters/replay`（order）** → **必须一分为二**。
- 依赖面（照 `docs/microservices-plan.md`）：activity = common + domain-model + infra + **product-core**（创建活动校验商品存在）；**不含 auth-core**；被 **bootstrap / ai / seckill** 三个模块依赖 → 改名要同步 3 处 + 父 POM。
- **管理令牌纵深防御在 auth-core**（`ApiAccessWebConfig` + `ApiAccessInterceptor`，还依赖 `UserSessionService` / `UserTokenRegistry`）→ **product 应用与新的 activity 应用都不会自带它**（Day 2 遗留缺口：直连 `:8084/api/admin/products/**` 无令牌校验，只有网关把关）。**本日决策**：接受"网关唯一准入"并记录；**收口日**把"仅 requireAdmin"的部分（`SecurityProperties` + 令牌校验）下沉到 `infra`，一次性恢复两个应用的纵深防御。
- 单体只剩 3 个文件：`AdminSeckillController` / `HealthController` / 启动类（`@MapperScan` 5 个域 + `@EnableScheduling`）。
- **新应用不要开 `@EnableScheduling`**：单体里它驱动的是 seckill/order/analytics 的定时任务；activity 应用不需要，开了有**重复执行**风险（Step 0 先 grep 确认 activity-core 无 `@Scheduled`）。
- 测试面：**没有** MockMvc 测试打 `/api/admin/seckill/**`（只有网关过滤器测试引用该路径字符串）→ 迁接口不打断现有测试，只需新增归属守卫断言。
- 预热锁：`SeckillCacheService.withActivityCacheLock`（Redisson）已在 activity 库 ✓ 随库进应用，**本日零代码改动**即完成"预热锁归属 activity"。

**关键设计：网关不能整段导流 `/api/admin/seckill/**`** —— 同前缀下还有 analytics 的 `GET /activities/{id}/metrics`、`POST /activities/{id}/snapshot` 与 order 的 `POST /dead-letters/replay`。必须精确三条：

```yaml
            - Path=/api/admin/seckill/activities,/api/admin/seckill/activities/*/preheat,/api/admin/seckill/activities/*/reset-stock
```

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 前置 | 停 5 服务；grep 确认 activity-core 无 `@Scheduled`；工作区 clean | 停服务再构建（老坑） |
| Step 1 改名 | `flash-sale-activity` → `flash-sale-activity-core`；父 POM（module + dependencyManagement，版本用 `${project.version}`）+ 引用面 3 处（bootstrap / ai / seckill） | 照 Day 1 改名提交的做法，一条原子提交 |
| Step 2 新应用 | 新建 `flash-sale-activity`（:8085）：POM 照 `flash-sale-product/pom.xml` 抄（activity-core + common/domain-model/infra + web/validation/mybatis-plus/mysql/redis/redisson/dotenv/actuator/micrometer/nacos-discovery + boot 插件 `workingDirectory`）；启动类放根包 + `@MapperScan("...activity.mapper")`、**不加 `@EnableScheduling`**；yaml（8085 / `flash-sale-activity` / **过渡期 datasource 仍指 `flash_sale`** / `module.db.dual-write` 段 / `classpath*:mapper/*.xml` / traceId 日志格式） | **新模块 POM 四条自查**：`</dependencies>` 位置、父 POM 只登记不继承（`<dependencyManagement>`）、`${project.version}`、启动类位置 |
| Step 3 接口拆分 | 新建 `activity.controller.AdminSeckillController`（3 个 activity 接口，**路径不变**）；单体侧 `AdminSeckillController` 精简为**只留 `dead-letters/replay`**（建议改名 `AdminMessageController`，路径不变） | **不要搬** service / mapper / cache / vo / dto（seckill、ai 依赖 core）；`dead-letters/replay` **绝不能**进 activity 应用（order 域） |
| Step 4 网关精确导流 | 在 `flash-sale-product` 之后、单体兜底之前插入 `id: flash-sale-activity` → 上面三条 Path | **不要**用 `/api/admin/seckill/**` |
| Step 5 验证 + 回归 + 文档 + 提交 | 构建 → 起 6 服务 → 判据 → 停服务回归 → plan.md Day 4 ✅ → 2~3 条原子提交 | 回退点：网关路由回退 + `git revert` |

**验收判据**：① `install` exit=0（reactor **17 模块**）；② Nacos **6 服务**、`:8085/actuator/health` UP（db/redis/nacos 全 UP）；③ 经网关活动管理端链路：`POST /api/admin/seckill/activities` → `code=0`、`.../{id}/preheat` → `code=0`（返回 stock/stockKey）、`.../{id}/reset-stock` → `code=0`；④ **路由精确性**：`GET /api/admin/seckill/activities/{id}/metrics`（analytics）与 `POST /api/admin/seckill/dead-letters/replay`（order）**仍由单体**提供；⑤ **反证**：停 `:8085` → 经网关三条 activity 路径 **5xx**（日志 `No servers available for service: flash-sale-activity`），metrics 仍 200；⑥ **直连单体** `POST /api/admin/seckill/activities` → **404**（已摘除），直连 `:8085` → 200；⑦ **双写回归**（Day 3）：经网关建/改/预热后 `flash_sale_activity.seckill_activity` 与主库逐字段一致，对账脚本 `8/8 consistent`；⑧ 其它域不变：`/api/seckill/activities`（seckill 域仍在单体）、`/api/rank/top10`、`/api/support/chat`、`/api/health`；⑨ `X-Trace-Id` 单值；⑩ 全量回归绿线（125 + 新增守卫断言）。

**坑**：① **整段导流 `/api/admin/seckill/**` 会抢走** analytics 的 metrics/snapshot 与 order 的 dead-letters；② `dead-letters/replay` 属 order 域，进 activity 应用 = 非法跨域依赖；③ 新模块 POM 四条自查（Day 1 全踩过）；④ **不加 `@EnableScheduling`**；⑤ 停服务再构建；⑥ 新应用**没有** auth-core 的 `/api/admin/**` 令牌校验（与 product 应用同）→ 直连 `:8085` 无令牌也放行，只有网关把关，收口日补；⑦ 改名后 `~/.m2` 里会残留旧 `flash-sale-activity-*.jar`（无害，排查依赖时注意）。

**提交计划**：① `refactor(p3-day4): activity 模块改名 flash-sale-activity-core（引用面 3 处同步）`；② `feat(p3-day4): 新建 flash-sale-activity 应用（:8085）+ 活动管理端接口迁入 + 网关精确导流`；③ `test(p3-day4)+docs: 归属守卫断言 + plan.md Day 4 记录`。

**Day 4 实施记录（2026-10-15）**：

- **改名**：`git mv` 库目录 + 父 POM（module 列表 + dependencyManagement）+ 引用面 3 处（bootstrap / ai / seckill）→ 构建 16 模块绿线（`705838e`），库内代码零改动。
- **新应用**：POM 照商品应用抄（含 boot 插件 `workingDirectory=${maven.multiModuleProjectDirectory}`）；启动类放根包 + `@MapperScan`；yaml **必须与单体同一个 Redis database（=1）** —— 否则本服务预热的 `seckill:activity:*` / `seckill:stock:*` 与单体的执行/校验路径对不上，会出现"预热成功但抢购读不到"。
- **首个启动失败（本日最有价值的坑）**：`SeckillActivityServiceImpl` 构造参数依赖 **product-core 的 `ProductMapper`**（创建活动要校验商品存在）→ 只扫 `activity.mapper` 会报 `No qualifying bean of type 'ProductMapper'`。修法：`@MapperScan({"…activity.mapper", "…product.mapper"})`（架构表允许 activity 依赖 product-core；将来把该校验换成调用 product 服务接口后，可收敛回单包）。
- **控制器拆分纪律**：`dead-letters/replay` 属 **order 域** → 留单体（改名 `AdminMessageController`）；迁进 activity 应用会让活动服务反向依赖 order，属非法依赖。**HTTP 路径全部不变**（前端与网关零感知）。
- **网关必须"精确"，不能"整段"**：同前缀下还有 analytics 的 `{id}/metrics`、`{id}/snapshot` 与 order 的 `dead-letters/replay` → 只列三条路径（`/activities`、`/*/preheat`、`/*/reset-stock`）；实测 metrics 与 replay 仍由单体处理。
- **预热锁归属**：`SeckillCacheService.withActivityCacheLock`（Redisson）本就在活动域库 → 随库进应用，**零代码改动**即完成「预热锁归属 activity」。
- **反证方法沿用 Day 2**：停 `:8085` 后等 ~40s（Nacos 摘除实例 + LB 缓存过期）→ 三条 activity 路径 **503** 且网关日志点名 `flash-sale-activity`；**同时** metrics 仍 200 → 证明路由精确（不是整段抢走）。
- **纵深防御缺口（记录在案，Day 5 补）**：`/api/admin/**` 的服务侧令牌校验在 **auth-core**（`ApiAccessWebConfig` + `ApiAccessInterceptor`），而 activity/product 应用按架构表**不含 auth-core** → 实测**直连 `:8085` 无令牌也返回 200**，当前只有网关把关。Day 5 把"仅 requireAdmin"部分（`SecurityProperties` + 令牌校验）下沉 `infra`，一次性恢复两个应用。
- **探针数据**：验证期间建了 `P3D4-ActA`（进行中，验预热）/ `P3D4-ActB`（未开始，验重置库存），双写 ON → 两库都写入；验证后已从**两个库同时删除**，清理后对账仍 **8/8 consistent**（行数回 20/20）。
- **一个脚本坑**：PowerShell 里 `$pid` 是**只读自动变量**（进程 ID），用它接 SQL 查询结果会静默失败（赋值报错、值不变）→ 本次首轮验证因此把 productId 传成进程号，全部拿到 `40004`；换名（如 `$probePid`）即可。

**回滚方式**：网关删除 `flash-sale-activity` 路由 + `git revert` 本日三条提交（读路径未切 → 业务无感）。

---

### P3 Day 5 详细任务卡（收口日：库存权属 + 纵深防御 + 去跨域直读）

> 执行方式：panda 实现 + 导师现场纠偏；预估 2.5~3.5 小时。

**目标**：把 P3 剩的三件事收口，并为 P4 的"读切换"扫清**最后一个前置条件**：

1. **库存权属收口**：activity 不再直写 `seckill:stock:*`，改调 seckill 域的内部接口（init / reset）；
2. **补齐 `/api/admin/**` 服务侧纵深防御**（Day 4 记录的缺口：直连 :8084/:8085 无令牌也放行）；
3. **去掉 activity 对 `product` 表的跨域直读**（改为调用 product 服务接口）—— 「读切换」的硬前置。

**Day 5 侦察结论（决定动作面与"读切换"的时间点）**：

- **`seckill:stock:*` 的写者只有 3 处**：`SeckillCacheService`（activity，preheat/reset 各一次 `redisTemplate.set`）、`decr_stock.lua`/`seckill_execute.lua`（seckill 扣减）、`SeckillOrderConsumer` 死信补偿 `increment` 回补。按设计"库存归 seckill"→ activity 的两处属**越权写**，本日收口。
- **DB 库存列没有任何 UPDATE 路径**：`SeckillActivityMapper.xml` / `ProductMapper.xml` 只有 `resultMap`（无 UPDATE 语句）；Java 侧对库存全是读与 `setXxx` → **库存真身在 Redis，DB 是配置值**。
- **两张表的 DB 写者只有两个应用**：生产代码里 `product` / `seckill_activity` 的 insert/update 只出现在 `ProductServiceImpl`（product 应用）与 `SeckillActivityServiceImpl` / `SeckillCacheService`（activity 应用）；**单体 / seckill / analytics / order / ai 全是只读**，且**没有任何** `createActivity` / `preheatActivity` / `resetStock` / `saveProduct` 调用点（已复核）。
  → 结论：**"翻转权威源"（应用写新库 + 反向镜像保旧库给未拆模块读）在技术上可行**，但有一个硬前置：**activity 应用现在直读 `product` 表**（`SeckillActivityServiceImpl` 用 `ProductMapper.selectById` 校验商品存在 + 应用 `@MapperScan` 扫了 `product.mapper`）→ 一旦把它的 datasource 切到 `flash_sale_activity`，该校验会因为**新库没有 product 表**而失败 ✗。
  → 因此本日先做第 3 件（去跨域直读），**读切换放到 P4 首日**（那时 seckill/ai/analytics 陆续迁成服务，旧库表才有下线条件），切换配方已记在下方"P4 首日配方"里。
- **无 internal 接口约定**（全仓 `/internal` 零命中）→ 本日新建，统配 `X-Internal-Token`（内网直连、不经网关，故必须自带令牌校验）。
- **管理令牌校验在 auth-core** 的 `ApiAccessInterceptor.requireAdmin`，其依赖仅 `SecurityProperties`（纯配置类）+ common 的异常/结果码 → **可下沉 `infra`**（`infra` 已依赖 common；R1 规则禁止把框架配置放 `common`，故 `infra` 是正确落点）。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 前置 | 停 6 服务；复核"单体无 `product`/`seckill_activity` 写调用点"（本卡已复核）；工作区 clean | 复核命令写进提交信息 |
| Step 1 库存权属 | ① seckill 域新增 `SeckillStockService`（`initStock` / `resetStock`：**唯一** Redis 库存写口，保留"仅未开始/已结束可重置"以外的**数据侧**幂等与 TTL 语义）；② 单体暴露 `POST /internal/seckill/activities/{activityId}/stock`（`X-Internal-Token` 校验 + `InternalTokenProperties`）；③ activity-core 定义**端口** `SeckillStockPort`，activity 应用给 `HttpSeckillStockAdapter`（`lb://flash-sale-monolith`），单体给 `LocalSeckillStockAdapter`（进程内调 `SeckillStockService`）→ **端口与适配器**，activity 域不反向依赖 seckill 模块 | 业务守卫（活动状态/时间窗）**留在 activity**；seckill 只管"写库存"；preheat 变成"两步"（先 seckill init 库存 → 再写 `seckill:activity:*` 与 `preheat_status`），失败要在 `preheat_status=PREHEAT_FAILED` 上体现 |
| Step 2 纵深防御 | `infra` 新增 `security/AdminTokenProperties` + `security/AdminAccessWebConfig`（只对 `/api/admin/**` 做令牌校验，独立于 auth-core 的会话/CORS 逻辑）；product / activity 应用自动获得；单体为"双校验"（与 auth-core 同规则、结果一致；P4 合并） | 令牌缺失/不匹配 → `40100`；`OPTIONS` 放行（与现行为一致） |
| Step 3 去跨域直读 | activity-core 的 `SeckillActivityServiceImpl` 改为依赖 `ProductCatalogPort`（`existsById` / `getPrice`）；activity 应用给 `HttpProductCatalogAdapter`（`GET /api/products/{productId}`，`lb://flash-sale-product`）；**撤掉 `@MapperScan` 里的 `product.mapper`**（Day 4 的过渡修法退场）；单测把 `ProductMapper` stub 换成 port stub | product 服务不可用时创建活动必须**明确失败**（不静默放行）；详情/校验路径不能因网络错误而误判 |
| Step 4 验证 | 起 6 服务 → 判据表（含反向证明） | 见下 |
| Step 5 回归 + 文档 + 提交 | 全量回归绿线 → plan.md Day 5 ✅ + **P3 收口小结**（含"读切换"配方与 P4 顺序）→ 3 条原子提交 | 回退点：`git revert` + 恢复配置 |

**验收判据**：① 构建 17 模块绿线；② Nacos 6 服务 + `:8085` health UP；③ **库存权属**：经网关预热活动 → 单体日志出现 `/internal/seckill/activities/{id}/stock` 请求、activity 日志**不再**出现库存写入；`reset-stock` 经网关仍 `code=0`；④ **越权阻断**：直连单体 `POST /internal/seckill/activities/1/stock` **无 `X-Internal-Token` → 40100**；⑤ **纵深防御**：直连 `:8084`/`:8085` 的 `/api/admin/**` 无令牌 → **40100**、带令牌 → 通过；经网关仍 `code=0`；⑥ **去跨域直读**：activity 应用 `@MapperScan` 只剩 `activity.mapper`；**停 `:8084` 后**经网关建活动 → 明确失败（`4xxxx`/5xx，**绝不静默通过**），恢复 `:8084` 后建活动成功；⑦ 其它域不变（`/api/seckill/activities`、`/api/rank/top10`、`/api/support/chat`、metrics、dead-letters）；⑧ **双写与对账无回归**：经网关建活动 → 两库一致、对账 `8/8 consistent`；⑨ 全量回归绿线（126 + 新增断言）；⑩ 文档：plan.md Day 5 ✅ + P3 收口小结。

**坑**：① 内部接口**必须**带令牌校验（否则等于把库存写口暴露给内网任何进程）；② `lb://` 调用需 `spring-cloud-starter-loadbalancer`，服务名必须与注册名一致（`flash-sale-monolith`）；③ preheat 跨进程两段式**不能**当本地事务（第一段成功、第二段失败时以 `PREHEAT_FAILED` + 运维重置兜底）；④ 撤 `@MapperScan(product.mapper)` 的同时必须去掉 `SeckillActivityServiceImpl` 的 `ProductMapper` 构造参数（否则启动期就报 `No qualifying bean`）；⑤ 单测 `CreateBusinessValidationTest` 用 `ProductMapper` stub → 换成 port stub；⑥ 单体"双校验"要在文档里写明（P4 合并）；⑦ 停服务再构建。

**提交计划**：① `feat(p3-day5): seckill 内部库存初始化/重置接口 + activity 端口化委托（库存权属收口）`；② `feat(p3-day5): infra 下沉管理令牌校验（product/activity 补齐 /api/admin/** 纵深防御）`；③ `refactor(p3-day5)+docs: activity 改调 product 服务接口校验商品（撤销跨域直读）+ plan.md 收口`。

**P4 首日配方（读切换 / 翻转权威源，本日不做）**：

1. **前置**（本日完成）：无单体跨域 DB 写 ✓、activity 不再直读 product 表 ✓；
2. **翻转**：product 应用 `spring.datasource.url` → `flash_sale_product`；activity 应用 → `flash_sale_activity`；`module.db.dual-write` 的**镜像目标改指旧库**（`flash_sale`）—— 未拆模块（seckill/ai/analytics 在单体）继续读旧库且由反向镜像保持新鲜；
3. **门禁**：对账脚本作为**切换前置门禁**（差异非 0 阻断）；切换后跑同一套判据（读己之写 + 反向镜像 + 对账 `8/8`）；
4. **退役条件**：seckill / ai / analytics 全部迁成服务并直接读新库后，删除反向镜像、旧库表下线。

**Day 5 实施记录（2026-10-15）**：

- **端口与适配器（本日的主结构）**：activity 域定义两个端口 —— `SeckillStockPort`（库存的唯一写出口）与 `ProductCatalogPort`（唯一的跨域读出口）；**同一端口、多种装配**：activity 应用给 HTTP 适配器（`lb://flash-sale-monolith` / `lb://flash-sale-product`，带 `X-Internal-Token`），单体给本地适配器（进程内直调 / 直读 mapper），ai 给本地商品端口 + **fail-fast** 库存端口（ai 不做库存写入，真被调到就应当响亮失败）。
- **库存权属收口**：`seckill:stock:*` 的"初始化/重置"写动作从 activity 的 `SeckillCacheService` 搬到 `flash-sale-seckill` 的 `SeckillStockService`（唯一写口，保留 SET 后必须 EXPIRE 的历史教训），对外经 `POST /internal/seckill/activities/{id}/stock` 暴露。**业务守卫（活动是否存在/进行中/时间窗）刻意留在 activity** —— 那是活动域语义；seckill 只管"把给定值写进去、设好 TTL"。
- **安全默认值**：`infra` 新增两个守卫 —— `AdminAccessWebConfig`（`/api/admin/**` + `X-Admin-Token`，与 auth-core 同 key `security.admin-token`，单体为"双校验"、规则一致）与 `InternalAccessWebConfig`（`/internal/**` + `X-Internal-Token`）。两者都 **fail-closed**：令牌没配 → 一律拒绝（避免"忘了配 = 后台/库存写口裸奔"）。放在 infra 而不是某个域，是为了让"任何应用新增管理端/内部接口都自动带上护栏"。
- **典型故障 1（启动失败，最有价值）**：activity 应用撤掉 `@MapperScan(product.mapper)` 后起不来 —— `ProductServiceImpl` 仍被组件扫描，它要 `ProductMapper`。**根因不是扫描，而是依赖面**：activity-core 已不再需要 product 的实体/服务/表。正解：**activity-core 去掉 `product-core` 依赖**（商品访问已端口化），并顺手把 `product-core` 显式补给 `flash-sale-seckill`（它过去是靠 activity-core 的**传递依赖**拿到 `ProductMapper` 的，属于隐性依赖，早晚要还）。这一刀同时也是 P4 读切换的前置条件：application 侧已经没有任何 product 表的代码路径。
- **典型故障 2**：ai 应用起不来（`SeckillCacheService` 要 `SeckillStockPort`）—— ai 扫到 activity-core 的缓存服务但没有该端口的实现。修法见上（本地商品端口 + fail-fast 库存端口），并在代码注释里写明"本类存在的唯一原因是组件扫描"。
- **典型故障 3**：`infra/db/DualWriteProperties.java` 第 35 行残留半截注解 `@Aut`（疑似编辑器/写入中断造成），构建报"找不到符号" → 已修复（这也是"构建绿线要天天跑"的理由：它在 Day 3/4 一直是绿的，坏点是后来出现的）。
- **运行期证据**：经网关建活动（`code=0`，校验走商品服务内部接口）→ 预热（`code=0`，`stock=50`）→ 重置未开始活动库存（`code=0`，`stock=60`）；单体日志 `内部接口写入活动库存完成` 2 次 + `库存写入成功（初始化/重置）` 2 次，activity 应用侧 0 次；内部接口无令牌/错令牌 → `40100`、带令牌 → HTTP 200；直连 :8084/:8085 管理端接口无令牌 → `40100`、带令牌 → `40001`（业务校验码，证明确实过了安全层）。
- **反证（停下游）**：停 :8084 后经网关建活动 → `code=50000`「查询商品摘要失败（内部接口异常）：No instances available for flash-sale-product」且**库里零写入**（绝不静默通过）；恢复 :8084 后建活动恢复 `code=0`。**一个诚实的观察**：同一时刻"活动广场"列表仍返回 `code=0` —— 因为命中列表缓存（读路径先读缓存），冷缓存时同样会明确失败。
- **测试面**：3 处手工构造服务的单测跟随端口化调整（库存断言从"直写 Redis 键"改为"经端口写了多少/TTL 多少"，含"已结束活动最短 TTL=60s"与"被守卫拦下时不得触碰端口"）；新增 `InternalEndpointSecurityTest`（3 条：无令牌/错令牌 → 40100，正确令牌 → 通过护栏）作为内部接口 fail-closed 的回归网。
- **探针数据**：`P3D5-ActC/D/E` 建后即从**两个库同时删除**，清理后行数回 20/20、对账仍 **8/8 consistent**。

**提交计划（实际）**：① `feat(p3-day5): 库存权属收口 + 内部接口令牌 + 管理端纵深防御`；② `refactor(p3-day5): activity 改调商品服务接口（撤销跨域直读 + 撤销 product-core 依赖）`；③ `docs(p3-day5): plan.md Day 5 完成 + P3 收口小结`。

---

### P4 Day 1 详细任务卡（切读：翻转权威源，三阶段迁移的第 2 步）

> 执行方式：panda 实现 + 导师现场纠偏；预估 2~3 小时。对应 `microservices-plan.md` 的「数据迁移三阶段：双写 → **切读** → 下线旧表」。

**目标**：把 product / activity 两个应用的**权威数据源切到独立 schema**（读 + 写都走新库），旧库 `flash_sale` 降级为「未拆模块（seckill / ai / analytics 仍在单体）的只读副本」，由**反向镜像**保持新鲜；对账脚本升级为**切换门禁**。P4 首日只动**配置与镜像语义**，不改业务代码。

**P4 Day 1 侦察结论（决定可行性）**：

- **未拆模块对 `product` / `seckill_activity` 两表零写**（复核 seckill / order / analytics / ai：无 `insert/update/delete`、无 `new Product()/new SeckillActivity()`）。单体里的 `SeckillServiceImpl` 只用 `SeckillCacheService.getActivityFromCache`（读 Redis）；analytics 只写自己的 `seckill_activity_snapshot` 表。
  → **切读后不存在"两个写者互相覆盖"**，这是切读能做的**硬前提**（P3 Day 5 已经把它做成事实）。
- 两个应用当前 `spring.datasource.url` 都指旧库 `flash_sale`，而 `DualWriteProperties.productUrl/activityUrl` 指新库 → **切读 = 两边对调**：datasource → 新库、镜像目标 → 旧库。
- 镜像的**默认值必须反过来**：Day 3~P3 末它是"影子副本"（默认关，关了没影响）；切读后它是**未拆模块的数据来源**（默认必须开，关了 = 未拆模块读到凝固的旧副本）。
- 两应用需要的表在新库中已存在（`flash_sale_product.product` / `flash_sale_activity.seckill_activity`），且应用侧已**没有** product 表代码路径（P3 Day 5 端口化）✓。
- **单体 / ai / gateway 零改动**：它们继续读旧库，由反向镜像喂数据。
- 幂等迁移脚本（全量重同步）+ 对账脚本（8 项：行数/聚合/指纹）都已就绪 → 对账脚本正好当**切换门禁**。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 门禁 | 停 6 服务；**先跑一次对账**，必须 `8/8 consistent` 才能往下；工作区 clean | 差异非 0 → **不许切**（先重同步） |
| Step 1 语义改名 | `DualWriteProperties`：`productUrl/activityUrl` → `legacyProductUrl/legacyActivityUrl`（yaml 键 `legacy-product-url` / `legacy-activity-url`）；镜像日志带上**目标库名**（`target=product(db=flash_sale)`）→ 切换期一眼看出方向 | 三个 yaml 同步（product / activity / 单体）；单体侧镜像从此**惰性**（可达到的跨域写 = 0），加注释说明 |
| Step 2 翻转配置 | 两个应用 yaml：`spring.datasource.url` → 各自新库；`module.db.dual-write.enabled` 默认改 `${DUAL_WRITE_ENABLED:true}`，镜像目标 = 旧库 | 单体 yaml 的 dual-write 段标注"已惰性" |
| Step 3 构建 + 起服务 | `install`（17 模块）→ 起 6 服务 → Nacos 6 + health UP | 停服务再构建 |
| Step 4 验证 | 见判据表（含"未拆模块读到新数据"的**端到端**证据） | 关键判据 ④ |
| Step 5 回归 + 文档 + 提交 | 全量回归绿线 → plan.md P4 Day 1 ✅ + 三阶段进度 → 2 条原子提交 | **回滚 = 改配置**（datasource 换回旧库 + 镜像关），1 分钟 |

**验收判据**：① 构建 17 模块绿线；② Nacos 6 服务 + `:8084`/`:8085` health UP（db 组件连的是新库）；③ **权威在新库**：经网关建商品/活动 → 新库有（权威）+ **旧库也有**（反向镜像，日志 `target=product(db=flash_sale)`）；④ **未拆模块读到新数据（本日最关键的证据）**：`GET :8090/api/seckill/activities`（单体 seckill 域，**读旧库**）能看到刚建/刚改的活动与最新字段；⑤ 活动预热链路不受影响：经网关 preheat `code=0`（库存仍走 seckill 内部接口 + Redis）；⑥ **读己之写**：`GET :8084/api/products/{id}` 返回刚写入的值；⑦ **切换后对账**仍 `8/8 consistent` + 行数一致（切换前门禁记录留底）；⑧ 其它域不变：rank / support / metrics / dead-letters；⑨ 全量回归 **129 用例**绿线；⑩ 文档：plan.md P4 Day 1 ✅ + 三阶段进度（双写 ✅ → **切读 ✅** → 下线旧表 ⏳）+ 回滚说明。

**坑**：① 切读后镜像**默认必须开**（与 P3 相反）；② 验证时**别再设** `DUAL_WRITE_ENABLED=false`（历史演示脚本设过，环境变量会覆盖默认）；③ 旧库里若有人手工改过数据，会被视为"过期副本"（以新库为准 —— 这正是切读的本义）；④ 切换用"停服务 → 改配置 → 起服务"避免半程写入；⑤ 主键是雪花 id（无自增依赖）✓ 两库 id 语义一致；⑥ 对账脚本要在**切换前**跑一次留底；⑦ 回滚无数据代价（双写仍在，两库一致）。

**提交计划**：① `feat(p4-day1): 切读 —— 两应用数据源切新库 + 反向镜像（旧库降级为副本）`；② `docs(p4-day1): plan.md P4 Day 1 完成 + 三阶段迁移进度`。

**P4 Day 1 实施记录（2026-10-15）**：

- **改动面极小、语义变化极大**：全仓只改 **2 个 Java 文件**（`DualWriteProperties` / `DualWriteMirror`，后者新增 `databaseOf()` 日志辅助）+ **3 个 yaml**，**零业务代码改动**。方向反转点：镜像目标从"新 schema"改为"旧库副本"（字段与键名同步改名为 `legacyProductUrl/legacyActivityUrl`、`legacy-*-url`）。
- **默认值的"方向性"**：`DualWriteProperties.enabled` **类内仍是 `false`**（安全默认值：没配 `module.db.dual-write` 的进程如单体 / ai **绝不误写第二个库**），而**两个已拆应用的 yaml 显式 `${DUAL_WRITE_ENABLED:true}`** —— 切读后它们是副本的唯一维护者，关掉 = 未拆模块读到凝固的旧副本。单体 yaml 的镜像段**直接删除**（它对两表可达到的跨域写为 0，且它自己就是旧库的读者）。
- **门禁先跑、前后留底**：切换前对账 `8/8`（`product 6/6`、`activity 20/20`、`preheat 1-19-0`）→ 切换后同口径 `8/8` → 探针清理后再 `8/8`。
- **运行期证据**：经网关建商品 / 建活动 → **权威库与副本库双存在**且字段一致；应用日志 `镜像成功（权威 -> 副本），target=product(db=flash_sale)` 与 `target=activity(db=flash_sale)`（**日志带目标库名**，是切读期防"方向搞反"的关键观测点）；预热 `code=0`（`stock=40`，两库 `preheat=1`）；**未拆模块经旧库读到新数据**：单体 `GET /api/seckill/activities` 含新活动且**带商品名/图**（顺带证明商品副本也新鲜）；应用 `GET :8084/api/products/{id}` 读己之写 ✓；其它域（rank / support / metrics / dead-letters）行为不变。
- **决定性判定（本日最值得学的一步）**：原打算用"改库 → 调 API 看结果"证明读方向 —— **被详情缓存骗了**（`product:detail:*` 命中，改权威库后 API 仍返回旧值）。改用**缓存冷路径 + 新 id 探针**：只在**权威库**插一行 → API `code=0` 且读到该行；只在**副本库**插一行 → API **`40004`**。两条一起才是"读权威、不读副本"的完整证明（只看第一条会误判为"通过"，因为两条路径都可能返回 40004）。
- **沙箱坑**：`CREATE TEMPORARY TABLE ... AS SELECT` 需要默认库上下文（`USE db;`），否则 `ERROR 1046 No database selected` —— 首轮探针因此**整批插入失败**，两条 GET 都回 `40004`，**判定不成立**（差点被当成"通过"）。
- **回滚**：两个应用 `spring.datasource.url` 换回 `flash_sale` + 镜像 `enabled: false`（或 `DUAL_WRITE_ENABLED=false`）→ **1 分钟**回到切换前；数据无代价（两库一致，镜像可随时重跑迁移脚本补平差量）。
- **三阶段迁移进度**：双写 ✅（P3 Day 3）→ **切读 ✅（本日）** → 下线旧表 ⏳（等 seckill / ai / analytics 全部迁成服务）。

---

### P4 Day 2 详细任务卡（seckill 域服务化：库 + 应用 :8086 + 网关精确导流）

> 执行方式：panda 实现 + 导师现场纠偏；预估 3~4 小时（含执行链路端到端验收）。

**目标**：`flash-sale-seckill`（库）改名 `flash-sale-seckill-core`；新建 `flash-sale-seckill` 应用（:8086）承载 4 个 HTTP 入口；抽出共享的**服务间 HTTP 适配器模块**（去掉两个应用间的重复适配器）；网关**精确四条 Path** 导流；**切权威库读**（应用数据源 = `flash_sale_activity`，不依赖旧库）。

**P4 Day 2 侦察结论（决定动作面与三处关键设计）**：

- **seckill 只有 7 个文件、零 DB 写**：`seckill/{controller×2, dto, service×3}`；全模块**没有** `insert/update`、没有 `@TableName`、**没有 `ProductMapper` 使用**（复核：`SeckillServiceImpl` 里 `productMapper` 零命中）。它的动作是"读活动 + Redis Lua 扣减 + 发 Stream"，**订单落库由 order 域的消费者在单体完成** → 天然的服务边界，拆分成本低。
- **HTTP 入口（4 条）**：`GET /api/seckill/activities`、`GET /api/seckill/activities/{id}`（公开读，委托 `activity-core` 的 `SeckillActivityService`）、`GET /api/seckill/activities/{id}/check`、`POST /api/seckill/execute`（需用户身份）。
- **同前缀下混着 order 域的路径**：网关注入的用户鉴权清单里有 `/api/seckill/users/*/orders`、`/api/seckill/users/me/orders`、`/api/seckill/orders/*` —— 这些是 **order 域**的 `OrderController`（仍在单体）→ 网关**绝对不能用 `/api/seckill/**`**（Day 4 的教训原样重演）。
- **用户身份机制**：`SeckillExecuteController` 从 request attribute `AUTHENTICATED_USER_ID` 取用户；该属性由 **auth-core 的 `ApiAccessInterceptor.requireUser`** 写入（会话 Cookie `FLASH_SESSION` 或 `X-User-Token` 校验后）→ 新应用**必须带 `flash-sale-auth-core`**（否则执行接口永远 40100）。
  ⚠️ 与架构表的依赖面（seckill = common/domain-model/infra/activity-core/product-core）**不符**：本日**显式决策**给 seckill 应用加 auth-core（与单体同一套机制、行为零变化），并在 `microservices-plan.md` 的依赖表里同步更新；后续收口日再考虑抽出"瘦用户会话守卫"下沉 infra。
  auth-core 的注册范围实测**只覆盖** `/api/admin/**`、`/api/seckill/execute`、`/api/seckill/activities/*/check`、`/api/seckill/users/*/orders`、`/api/seckill/users/me/orders`、`/api/seckill/orders/*`、`/api/auth/{me,csrf,logout}` → **两个公开读端点不在拦截名单里** ✓ 加进应用后行为与单体一致。
- **`SeckillStockPort` / `ProductCatalogPort` 两个端口的实现装配**（P3 Day 5 铺的端口，这里收果子）：
  - **库存端口** → 应用侧必须给**本地实现**（`SeckillStockService` 就在 seckill-core 里 ✓）：把 `LocalSeckillStockAdapter` **从 bootstrap 移进 seckill-core**（单体与 seckill 应用都自动获得，bootstrap 删副本）；
  - **商品目录端口** → 应用侧给 **HTTP 适配器**（activity-core 的 `SeckillActivityService` 组装列表/详情要用它）。
- **因此值得抽出共享模块 `flash-sale-remote`**：把 activity 应用里的 `HttpSeckillStockAdapter`、`HttpProductCatalogAdapter`、`LoadBalancedClientConfig` 三个类提出来，activity 与 seckill 应用都依赖它（避免第二个应用复制 150 行适配器）。**只有需要 HTTP 适配器的应用依赖它**，单体（本地适配器）、ai（过渡期本地读）都不依赖 → 零 Bean 冲突。
- **`product-core` 依赖可摘**：seckill 对 `ProductMapper` 零使用（P3 Day 5 我基于旧侦察给它显式加过这条依赖，本条修正之）→ 摘掉后应用侧**不存在** `ProductServiceImpl`（也就不会重演 Day 5 "只扫 activity.mapper 导致 `ProductMapper` 缺 Bean"的启动失败）。
- **数据源**：应用**只读**活动数据 → 直接指**权威库 `flash_sale_activity`**（Day 4 的"过渡期读旧库"在这里不需要：seckill 对两表零写，读权威反而更新鲜、且不欠旧库债）。
- **无 `@Scheduled`**：seckill 模块无定时任务（定时任务只在 order 的 Stream 消费调度）→ 新应用**不加 `@EnableScheduling`**。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 前置 | 停 6 服务；工作区 clean；记录基线（两库行数 + 对账 `8/8`） | 停服务再构建（老坑） |
| Step 1 改名 | `git mv flash-sale-seckill flash-sale-seckill-core`；POM（artifactId/name/description）；父 POM（module + dependencyManagement）；引用面只有 **bootstrap 1 处** | 顺手**摘掉 `product-core`** 依赖（零使用） |
| Step 2 抽共享适配器 | 新模块 `flash-sale-remote`（依赖 common/infra/activity-core + spring-web + loadbalancer）：搬入三个类；activity 应用 POM 改依赖它；`LocalSeckillStockAdapter` 从 bootstrap 移入 `seckill-core`（bootstrap 删副本） | `flash-sale-remote` **不被**单体/ai 依赖 → 无 Bean 冲突；每模块 POM 四条自查 |
| Step 3 新应用 | 新建 `flash-sale-seckill`（:8086）：POM = seckill-core + common/contracts/domain-model/infra/**auth-core**/**flash-sale-remote** + web/validation/mybatis-plus/mysql/redis/redisson/dotenv/actuator/micrometer/nacos-discovery + boot 插件；启动类根包 + `@MapperScan("…activity.mapper")`（**不扫 product.mapper**）+ 不加 `@EnableScheduling`；yaml（8086 / **datasource = flash_sale_activity** / 同一个 Redis database=1 / `module.internal.token` / `security.admin-token`） | 新模块 POM 四条自查（Day 1/4 都踩过） |
| Step 4 网关精确导流 | 在 `flash-sale-activity` 之后、单体兜底之前插入 `id: flash-sale-seckill` → **四条**：`/api/seckill/activities`、`/api/seckill/activities/*`、`/api/seckill/activities/*/check`、`/api/seckill/execute` | **不要** `/api/seckill/**`（会抢走 order 域的 `/orders/*`、`/users/*/orders`） |
| Step 5 验证 + 回归 + 文档 + 提交 | 构建（**18 模块**）→ 起 **7 服务** → 判据表（含 `execute` 端到端）→ 停服务回归 → plan.md P4 Day 2 ✅ → 3 条原子提交 | 回退点：网关路由回退 + `git revert` |

**验收判据**：① `install` 绿线（reactor **18 模块**）；② Nacos **7 服务** + `:8086/actuator/health` UP（db 连 `flash_sale_activity`）；③ **公开读链路**：`GET /api/seckill/activities` 200 且**带商品名/图**（走 `flash-sale-remote` 的 HTTP 适配器 → 商品服务内部接口）、`GET /api/seckill/activities/{id}` 200；④ **执行链路端到端（本日核心）**：注册/登录取用户令牌 → `POST /api/seckill/execute` → 成功；**Redis 库存 -1**；**Stream 有消息**；**订单在单体侧落库**（跨服务事件仍闭环）；同一用户重复执行 → `DUPLICATED`；**无令牌 → 40100**；⑤ **路由精确性**：`GET /api/seckill/users/{id}/orders`、`GET /api/seckill/orders/{no}`（order 域）**仍由单体**提供；⑥ **反证**：停 `:8086` → 四条 seckill 路径 503（网关日志 `No servers available for service: flash-sale-seckill`）而 order 查询路径仍 200；⑦ 直连对照：直连单体 `POST /api/seckill/execute` → **404**、直连 `:8086` 同路径 → 非 404；⑧ 双库与对账无回归（`8/8`）；⑨ 其它域不变（products / rank / support / metrics / dead-letters）；⑩ 全量回归绿线。

**坑**：① 网关**四条精确 Path**（同前缀混 order 域路径）；② 应用**必须带 auth-core**（否则 execute 恒 40100）—— 这是对架构表依赖面的**有意偏离**，要同步文档；③ `SeckillStockPort` 给**本地**实现、`ProductCatalogPort` 给 **HTTP** 实现（装反了启动即失败/行为错）；④ **不扫 product.mapper**（只要 seckill 不再依赖 product-core）；⑤ 数据源指**权威库**；⑥ 不加 `@EnableScheduling`；⑦ `flash-sale-remote` 不能被单体/ai 依赖（否则 HTTP 与本地适配器同类型 Bean 冲突）；⑧ 若注册/登录接口不可用，判据④ 降级为"无令牌 40100 + 反证"，并在实施记录里写明。

**提交计划**：① `refactor(p4-day2): seckill 模块改名 flash-sale-seckill-core（摘掉零使用的 product-core）`；② `refactor(p4-day2): 抽出共享 HTTP 适配器模块 flash-sale-remote + 库存本地适配器归位 seckill-core`；③ `feat(p4-day2): 新建 flash-sale-seckill 应用（:8086）+ 网关精确导流`；④ `docs(p4-day2): plan.md P4 Day 2 完成 + 依赖表更新`。

**P4 Day 2 实施记录（2026-10-15）**：

- **改动面**：改名 `seckill → seckill-core`（引用面 3 处）+ 新模块 `flash-sale-remote` + 新应用 `flash-sale-seckill`（:8086）+ 网关 4 条精确 Path + 两个控制器搬迁（**零代码改动**，只是换了模块）+ 1 条归属守卫断言；reactor **19 模块**、Nacos **7 服务**、`:8086` health（db/redis/nacos）UP。
- **执行链路端到端（本日核心，M5「跨服务交易闭环」达成）**：注册/登录取用户态 → `POST /api/seckill/execute` → **`code=0`、`result=QUEUED`**、`orderNo=SK202610152353224330000`；同一用户再打 → **`40901` + `result=DUPLICATED`**（限购生效）；无令牌 → **`40100`（请先登录）**；**事件闭环**：单体日志 `SeckillOrderConsumer - 订单落库成功并ACK, orderNo=SK2026...`，`seckill_order` 与 `seckill_message_log` 各命中 1 行，`GET /api/seckill/orders/{no}` → `code=0 status=CREATED`。
- **路由精确性（第二个关键判据）**：同前缀下 order 域的 `GET /api/seckill/users/{id}/orders`（`code=0` 条数=1）与 `/api/seckill/orders/{no}` **仍由单体**提供；停 `:8086` → `GET /api/seckill/activities` **503** 且网关日志点名 `No servers available for service: flash-sale-seckill`，而 order 域路径仍 `code=0` ✓（证明确实是"四条精确 Path"，不是整段抢走）。
- **三个典型故障**：
  1. **`auth-core` 带进 `UserAccountService`** → 启动报 `UserAccountMapper` 缺 Bean。**根因不是扫描，是数据位置**：用户表（`user_account` 等）**只在旧库**（auth 域未拆，旧库就是用户数据的权威）→ 数据源必须指旧库 `flash_sale`（**卡片勘误**：卡里原写"数据源指权威库"，实际不可行）+ `@MapperScan` 补 `user.mapper`。代价：活动数据读的是旧库副本（反向镜像毫秒级保鲜）；**待办**：auth 域服务化、或把"瘦用户会话守卫"下沉 infra 后，再切回权威库。
  2. **端口双实现冲突**：`flash-sale-remote` 的 `HttpSeckillStockAdapter` 与 seckill-core 的 `LocalSeckillStockAdapter` 同时被扫 → `SeckillCacheService` 注入失败（两个 `SeckillStockPort`）。**修法不是加 `@Primary`，而是回到结构**：只有 activity 应用需要"跨进程写库存" → HTTP 库存适配器**退回 activity 应用**，共享模块只留"两边都要用"的商品目录适配器 ✓。
  3. **控制器没搬 = 双份入口**：直连单体 `GET /api/seckill/activities` 竟返回 **200** —— 控制器还留在 seckill-core 库里、被单体一并扫描（Day 4 是"控制器随域迁入应用"，本日最初漏了这一步）。`git mv` 两个控制器进应用（包名不变、零代码改动）→ 直连单体 **404** ✓。**教训**：库被多个运行时共用时，"HTTP 入口属于谁"必须用**文件位置**表达，而不是"谁能扫到"。
- **`POST /api/seckill/execute` 直连单体的诚实说明**：返回 **200 + `{"code":40100,"message":"请先登录"}`**（不是 404）—— 该路径在 auth-core 的用户态拦截器名单里，**拦截器先拒**（与 Day 4 的 `/api/admin/**` 同理）。端点确实已搬走（公开读路径直连 404 可证），守卫断言因此也用**类路径归属**而非 HTTP 状态。
- **旧库有 FK、新 schema 零 FK（本日新发现）**：旧库挂着 5 条 FK（`seckill_activity→product`、`seckill_order→seckill_activity/product`、`snapshot→activity`、`session→user`），而 Day 3 建的四个新 schema **一条 FK 都没有**。含义：① 反向镜像写旧库受 FK 约束（顺序必须与业务一致：先商品、再活动、后订单）；② 验证期清理探针时被 FK 挡住（订单引用活动、活动引用商品）→ 最终判定**把权威库缺的两行从副本补回**（两库重新一致 + 保留订单这条闭环证据）。
- **门禁与回归**：探针处理后对账 **8/8 consistent（exit=0）**；全量回归 **130 用例 0 失败 0 错误 BUILD SUCCESS**（1.8 分钟，19 模块）。
- **回退**：网关删 `flash-sale-seckill` 路由 + `git revert` 本日提交（读路径未变，业务无感）。

---

### P4 Day 3 详细任务卡（order 域服务化：应用 :8087 + 消费入口迁移 + 网关精确导流）

> 执行方式：panda 实现 + 导师现场纠偏；预估 3~4 小时。

**目标**：`flash-sale-order` → `flash-sale-order-core`；新建 `flash-sale-order` 应用（:8087）承载三个订单查询入口 + 死信回放 + **Stream 消费调度**；网关精确导流。**数据源仍指旧库**（`seckill_order` / `seckill_message_log` 尚未拆 schema，本日不碰数据）。

**P4 Day 3 侦察结论（决定动作面与两处关键取舍）**：

- **order 只有 11 个文件**：`controller/OrderController`（3 个查询入口）、`consumer/SeckillOrderConsumer`、`scheduler/StreamConsumerScheduler`、`service/{OrderQueryService, DeadLetterReplayService}`、`adapter/OrderQueryAdapter`、2 个 mapper + 1 个 XML、1 个 request DTO。
- **HTTP 入口（本日迁出 4 条）**：`GET /api/seckill/orders/{orderNo}`、`GET /api/seckill/users/{userId}/orders`、`GET /api/seckill/users/me/orders`（都取 `AUTHENTICATED_USER_ID` ✓ 与 seckill 应用同一机制 → **应用要带 auth-core**）+ `POST /api/admin/seckill/dead-letters/replay`（现居 bootstrap 的 `AdminMessageController` ✗ 属 order 域 → 随域迁走）。
- **⚠️ 消费入口的取舍（本日最关键的一刀）**：`SeckillOrderConsumer`（建单 + ACK + 死信）被 **4 个集成测试注入**（`SeckillConsumerIntegrationTest` / `SeckillMessageReliabilityTest` / `SeckillMetricsTest` / `SeckillRankTest`）→ **消费者必须留在 `order-core`**；真正要迁的是**调度入口** `StreamConsumerScheduler`（`@Scheduled` 两个：新消息 150ms、PEL 重试 5s，`@ConditionalOnProperty(flash.stream.auto-poll=true)`）。
  → 单体失掉调度器后**不可能再自动消费**（类不在 classpath 上，配置也改成 `false`，双保险）；应用侧 `@EnableScheduling` + `auto-poll=true` 接管。
  → **重复消费 = 重复建单**，这是本日唯一的高危点（守卫断言会把它钉住）。
- **依赖闭包（决定了应用 POM）**：`SeckillOrderConsumer` 经 `analytics.service.SeckillRankService` 写榜单 ZSet ✗ → order-core **必须带 `flash-sale-analytics`**；而 analytics 只依赖 common/contracts/domain-model/infra（**不碰 activity-core** ✓ 因此不需要库存端口 stub ✓），其唯一 mapper 是 `SeckillActivitySnapshotMapper`（`seckill_activity_snapshot` 表在旧库 ✓）→ 应用 `@MapperScan` = `order.mapper` + `analytics.mapper` + `user.mapper`（auth-core 的用户表 ✓）。
- `OrderQueryAdapter`（analytics 的 `OrderQueryPort` 实现，解 order→analytics 环）**留在 order-core**：单体（analytics 在单体）与 order 应用同用 ✓ 不冲突（analytics 不在两个运行时之外的地方 ✓）。
- `StreamConsumerScheduler` 与 `OrderController` 的 import 只涉及 common / auth-core 常量 / 域实体与服务 → **搬迁零代码改动**（只换模块）。
- 网关当前 order 域路径靠**单体兜底** ✓ → 本日必须精确切开（否则停 `:8087` 不会 503、搬运无从验证）。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 前置 | 停 7 服务；工作区 clean；记录基线（对账 8/8 + 订单/日志表行数） | 停服务再构建 |
| Step 1 改名 | `git mv flash-sale-order flash-sale-order-core`；POM 坐标；父 POM（module + dependencyManagement）；bootstrap 引用面 | 引用面只有 2 处 |
| Step 2 新应用 | 新建 `flash-sale-order`（:8087）：POM = order-core + **analytics** + **auth-core** + common/domain-model/infra + web/validation/mybatis-plus/mysql/redis/redisson/dotenv/actuator/micrometer/nacos + boot 插件；启动类根包 + `@MapperScan({order,analytics,user}.mapper)` + **`@EnableScheduling`**；yaml（8087 / **datasource = 旧库 flash_sale**（表还没拆，且 auth 用户表也只在那里）/ Redis database=1（榜单 ZSet 与单体共用）/ security.* / `flash.stream.auto-poll: true` / 不配 dual-write（零跨域写）） | 库模块 POM 四条自查 |
| Step 3 入口迁移 | `git mv` 三个文件进应用：`order/scheduler/StreamConsumerScheduler`、`order/controller/OrderController`、bootstrap 的 `controller/admin/AdminMessageController`（包名改 `order.controller`，类名保持）→ 零代码改动；单体 yaml `flash.stream.auto-poll: false`（+ 注释说明调度器已随域迁走） | **消费者本体留在 order-core**（测试要用） |
| Step 4 网关 | 在 `flash-sale-seckill` 之后、单体兜底之前插入 `flash-sale-order` → `Path=/api/seckill/orders/*,/api/seckill/users/*/orders,/api/seckill/users/me/orders,/api/admin/seckill/dead-letters/replay` | 全前缀 `/api/seckill/**` 会抢走 seckill 域（Day 2 的反向教训） |
| Step 5 验证 + 回归 + 文档 + 提交 | `install`（**20 模块**）→ 起 **8 服务** → 判据表（含"消费只在应用侧发生"的双向证据）→ 全量回归 → plan.md P4 Day 3 ✅ → 3 条原子提交 | 回退点：网关路由 + `git revert` |

**验收判据**：① 构建绿线（reactor **20 模块**）；② Nacos **8 服务** + `:8087/actuator/health` UP；③ **消费链路迁到应用（正向）**：经网关执行秒杀 → **新应用日志** `订单落库成功并ACK` + `seckill_order` 命中 1 行 + **榜单更新**（`GET :8090/api/rank/top10` 含该用户，读的是同一个 Redis）；④ **单体不再消费（反向，本日最关键）**：单体日志**无** `订单落库成功`/消费行；守卫断言钉住 `StreamConsumerScheduler` **不在单体 classpath**；⑤ **路由精确性**：三条 order 路径经网关由新应用提供；停 `:8087` → 三条路径 **503**，而 `/api/seckill/execute`、`/api/seckill/activities` 仍 **200**；⑥ 死信回放：经网关 `POST /api/admin/seckill/dead-letters/replay` 带 admin 令牌 → 业务码（证明过了 `/api/admin/**` 护栏）；无令牌 → `40100`；⑦ 直连对照：直连单体 `GET /api/seckill/orders/{no}` → `200 + 40100`（**拦截器先拒**，同 Day 2 说明）；`GET /api/admin/seckill/dead-letters/replay` 直连单体 → **404**（公开读路径可直接判）；⑧ 对账 **8/8** + 行数一致；⑨ 其它域不变（products / rank / support / metrics）；⑩ 全量回归绿线（130 + 新增守卫断言）。

**坑**：① **重复消费**（调度器必须走、单体配置必须关，双保险）；② 消费者本体**不能搬**（4 个测试注入）；③ 应用 `@MapperScan` 要含 `analytics.mapper`（`SeckillActivitySnapshotMapper`）与 `user.mapper`（auth-core）；④ 应用必须带 auth-core（否则 order 查询恒 40100）；⑤ 数据源指旧库（表未拆）→ 旧库下线要等 order schema 拆分日；⑥ 榜单 ZSet 在同一个 Redis database=1（否则榜单静默不更新）；⑦ 网关四条精确 Path，别用整段前缀；⑧ 单体 yaml 的 `auto-poll` 改 false 后，**集成测试自行驱动消费**的既有约定不变。

**提交计划**：① `refactor(p4-day3): order 模块改名 flash-sale-order-core`；② `feat(p4-day3): 新建 flash-sale-order 应用（:8087）+ 消费入口迁移 + 网关精确导流`；③ `test+docs(p4-day3): 归属守卫断言 + plan.md Day 3 完成`。

**P4 Day 3 实施记录（2026-10-16）**：

- **改动面**：改名 `order → order-core` + 新应用 `flash-sale-order`（:8087）+ **3 个入口文件迁移（零代码改动，只换模块）**: `order/scheduler/StreamConsumerScheduler`、`order/controller/OrderController`、bootstrap 的 `controller/admin/AdminMessageController`（包名归位 `order.controller`）；网关 4 条精确 Path；1 条守卫断言。reactor **20 模块**、Nacos **8 服务**、`:8087` health（db/redis/nacos）UP。
  > 提交①与②合并为一次提交：父 POM 的模块登记必须原子（先登记 `<module>flash-sale-order</module>` 却无目录会让构建失败）——卡里计划的 3 条提交实际落成 2 条。
- **消费链路迁移（本日核心，正反双向证据）**：经网关执行秒杀 → **`:8087` 日志 `[scheduling-1] 订单落库成功并ACK, orderNo=SK202610161249339790000`**；`seckill_order` 命中 1 行（`status=CREATED`）；**榜单同步更新**（`/api/rank/top10` 含探针用户 —— 写端在新应用、读端在单体，靠同一个 Redis database=1）；**单体 `订单落库成功` = 0 次** ✓。
- **防重复消费的两重保险**（本日唯一高危点）：① 调度器类只在应用里 —— 守卫断言钉住 `order.scheduler.StreamConsumerScheduler` **不得在单体 classpath**（否则单体的 `@EnableScheduling` 会双份建单）；② 单体 yaml `flash.stream.auto-poll: false`。**消费者本体 `SeckillOrderConsumer` 故意留在 order-core**（4 个集成测试注入它驱动消费）。
  > 诚实说明：单体日志仍有 2 行 `消费者组已存在 / 初始化完成`（`[restartedMain]`）—— 那是消费**Bean 的启动建组日志**，不是消费；真正的判据是"`订单落库成功` 0 次 + 消息已被应用 ACK"。
- **路由精确性**：三条 order 路径经网关 `code=0`（`/users/me/orders`、`/users/{id}/orders`、`/orders/{no}`）；停 `:8087` → `/api/seckill/users/me/orders` **503**（网关日志点名 `No servers available for service: flash-sale-order`）而 `/api/seckill/activities` 仍 **200** ✓。
- **死信回放**：无令牌 `40100`、带令牌 `40004`（业务码"死信消息不存在"= 过了 `/api/admin/**` 护栏）；**直连对照**：带令牌直连单体同路径 **404**（端点确实搬走）而直连 `:8087` **200** ✓。
- **依赖闭包的处理**：`SeckillOrderConsumer` 经 `SeckillRankService` 依赖 analytics → 应用必须带 `flash-sale-analytics`；好在 analytics 只依赖 common/contracts/domain-model/infra（**不碰 activity-core**）→ 应用 `@MapperScan` 三包（order/analytics/user）即可，**不需要任何端口 stub**。`OrderQueryAdapter`（analytics 的 `OrderQueryPort` 实现）留在 order-core：单体侧 analytics 与本应用同用。
- **数据源仍是旧库**：order 的三张表未拆 schema，且 auth 用户表也只在那里 → **旧库下线要等"order schema 拆分日"（Day 4）**；届时 order 的镜像方向应与 product/activity 一致（权威 = 自己的 schema、副本 = 旧库）。
- **门禁与回归**：全量 **131 用例 0 失败 0 错误 BUILD SUCCESS**（1.8 分钟，20 模块）；对账 **8/8 consistent（exit=0）**；探针 `P4D3-ProdA` / `P4D3-ActA` 与探针订单**保留**（订单经 Stream 落库的闭环证据；旧库 FK 链 订单→活动→商品 不允许单独删上游），两库始终一致 ✓。
- **小瑕疵（诚实记录）**：守卫测试改动被我 `git add` 整个 `flash-sale-bootstrap` 目录带进了提交 ①（本应独立成一条 test 提交）；不影响正确性。

---

### P4 Day 4 详细任务卡（order 的「库」拆分：flash_sale_order + 幂等迁移 + 反向镜像 + 对账门禁）

> 执行方式：panda 实现 + 导师现场纠偏；预估 2~3 小时。

**目标**：把 order 域两张表（`seckill_order` / `seckill_message_log`）迁到独立 schema `flash_sale_order` —— **权威 = 新 schema**（order 应用读写），**副本 = 旧库 `flash_sale`**（单体侧 analytics 的订单读取继续用它），由**反向镜像**保鲜。与 product/activity 完全同法（P3 Day 3 双写 → P4 Day 1 切读），本日只做 order。

**侦察结论（决定可行性与动作面）**：

- **单一写者前提成立**：P4 Day 3 之后 order 的 HTTP 入口与消费调度都在 `flash-sale-order` 应用；单体只剩消费者 Bean（无调度）与 `DeadLetterReplayService`（无入口）→ **可达到的写点为 0** ✓（与 Day 1 切读的硬前提同形）。
- **写点共 6 处**：`SeckillOrderConsumer`：`seckillOrderMapper.insert`(198)、`seckillMessageLogMapper.insert`(284)、`updateById`×3(296/308/314)；`DeadLetterReplayService.updateById`(120)。
- **两表列（旧库实测）**：`seckill_order`(id, order_no, activity_id, product_id, user_id, seckill_price, status, stream_message_id, failure_reason, created_at, updated_at) **4027 行**；`seckill_message_log`(id, stream_key, stream_message_id, consumer_group, consumer_name, order_no, activity_id, product_id, user_id, retry_count, status, error_message, created_at, updated_at) **5174 行**。
- **榜单写不受影响**：消费者调的 `SeckillRankServiceImpl.recordSuccess` 是**纯 Redis**（ZADD NX 幂等，零 DB）✓ → order 换库后榜单照写（读端在单体，同一个 Redis）。
- **⚠️ analytics 的 mapper 会跟着进 order 应用**：P4 Day 3 的装配扫了 `analytics.mapper`（`SeckillActivitySnapshotMapper`），而该表**不在** `flash_sale_order` 里 —— 启动只建 mapper Bean（MyBatis 不校验表存在 ✓），snapshot 查询路径不在本应用路由上 ✓。**记为已知取舍**：analytics 服务化那天消掉。
- **迁移脚本范本**：`db/migration/V20261015__split_product_activity_schema.sql`（`CREATE DATABASE/TABLE IF NOT EXISTS … LIKE` + `REPLACE INTO … SELECT` + 行数校验）→ 本日照抄为 `V20261016__split_order_schema.sql`。

**范围裁剪（本日的重要判断）**：原计划同时做"ai 目录改走服务接口"。实测 **`AiChatServiceImpl` 里还直连两处 mapper**（`ProductMapper` 列表查询 + `SeckillActivityMapper`），不只是 P3 Day 5 端口化过的那个适配器 → 完整迁移需要**新增"活动目录端口" + 内部接口 + 三处适配器实现**，是独立一天的工作量；半途迁移没价值（ai 仍读旧库）。→ **本日只做 order 库拆分**，ai 目录端口化 + order→analytics 解耦列入 Day 5（清单写进实施记录）。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 0 前置 | 停 8 服务；工作区 clean；**对账留底**（含 order 行数 4027 / 5174） | 差异非 0 不许迁 |
| Step 1 DDL + 迁移 | 写 `V20261016__split_order_schema.sql`（建库 + `LIKE` 两表 + `REPLACE` 全量 + 校验 SELECT）并执行 | `LIKE` 不复制外键（与 P3 同）；用 `REPLACE` 保幂等 |
| Step 2 镜像组件 | `DualWriteProperties` += `legacyOrderUrl`（键 `legacy-order-url`）；`DualWriteMirror` += `mirrorOrder(…)` | 复用既有 `mirror()` 内核（日志已带目标库名 ✓） |
| Step 3 写点接镜像 | order-core 新增小工具 `order.mirror.OrderMirror`（两条 upsert SQL：order / message_log，`INSERT … ON DUPLICATE KEY UPDATE`），6 个写点各加一行；消费者与死信服务构造函数加参数（Spring 注入 → 4 个集成测试不受影响 ✓） | 镜像**默认关** → 单体/测试零行为变化 |
| Step 4 切权威 | order 应用 yaml：datasource → `flash_sale_order`；`module.db.dual-write`（`enabled: true` + `legacy-order-url`） | 单体 yaml 不动（无 order 写路径） |
| Step 5 对账扩展 | `scripts/reconcile-product-activity.ps1` 增 **2 条 order 检查**（行数 + 内容指纹，与既有 8 项同形）→ 迁移前后各跑一次 | 脚本仍是切换门禁 |
| Step 6 验证 + 回归 + 文档 + 提交 | 8 服务 → 判据表（含权威/副本双向证据）→ 回归 → Day 4 ✅ → 提交 | 回退 = datasource 换回旧库 + 镜像关 |

**验收判据**：① 构建 **20 模块**绿线；② Nacos **8 服务** + `:8087` health UP（db 连 `flash_sale_order`）；③ **迁移门禁**：迁移前对账一致（含 order）→ 迁移后一致；`flash_sale_order` 两表行数 = 旧库 = **4027 / 5174** ✓；④ **权威在 order schema**：经网关执行一次秒杀 → 新 `flash_sale_order.seckill_order` 命中该订单 ✓ + 应用日志 `镜像成功（权威 -> 副本），target=order(db=flash_sale)` ✓ + **旧库副本同有** ✓；⑤ **单体读副本仍工作（切读证据）**：`GET /api/rank/top10`（单体 analytics 经 `OrderQueryAdapter` 读 `seckill_order`）返回该用户/订单号 ✓；⑥ 死信回放写 `seckill_message_log` → 权威有 ✓ + 副本有 ✓；⑦ 路由与反证：带会话经网关查一条订单 `code=0`；停 `:8087` → 三条 order 路径 **503** 而 seckill 域仍 **200** ✓；⑧ 对账（**10 项**）一致；⑨ 其它域不变（products / support / metrics / ai）；⑩ 全量回归绿线 + 文档（含 ai 迁移清单与 analytics 取舍）。

**坑**：① 迁移脚本在**停服务**时跑（避免半程写入）；② 镜像 SQL 必须**覆盖全部业务列**（`ON DUPLICATE KEY UPDATE`），否则 `updateById` 的镜像会漏列；③ 镜像默认关 → 忘了在应用 yaml 开 `enabled` 会让副本凝固（判据 ⑤ 专抓这个）；④ `analytics.mapper` 仍要扫（否则启动失败）；⑤ 旧库 `seckill_order` 有指向活动/商品的 FK，新 schema 的 `LIKE` **不带** FK ✓，但反向镜像写旧库受 FK 顺序约束（先活动/商品后订单，现状天然满足 ✓）；⑥ 迁移后**别再**用旧库行数做业务判断（权威已在新库）；⑦ 迁移期间老数据用 `REPLACE` 覆盖，新写入靠镜像 —— 两者不冲突（主键雪花 id ✓）。

**提交计划**：① `feat(p4-day4): order 独立 schema + 幂等迁移 + 反向镜像（旧库降级为副本）`；② `docs(p4-day4): 对账扩展 + plan.md Day 4 完成 + ai/analytics 遗留清单`。

**P4 Day 4 实施记录（2026-10-16）**：

- **改动面**：新增 `db/migration/V20261016__split_order_schema.sql`（已执行）；`DualWriteProperties` += `legacyOrderUrl`、`DualWriteMirror` += `mirrorOrder`；order-core 新增 `order.mirror.OrderMirror`（两条 upsert SQL，`IFNULL(?, NOW())` 处理时间戳）+ **6 个写点接入**；order 应用 datasource → `flash_sale_order` + 开镜像 + **双数据源过渡桥** `order.config.DataSourceConfig`；对账脚本 8 → **14 项**（+6 项 order）。
- **迁移门禁**：`order.rows 4027/4027`、`msglog.rows 5174/5174`；新 schema 两表 **FK 数 = 0**（`LIKE` 不复制外键）。
- **运行期证据**：执行一次秒杀 → 权威 `flash_sale_order.seckill_order` 命中（`status=1`）+ **副本同有** + 应用日志 `镜像成功（权威 -> 副本），target=order(db=flash_sale)` **3 次**（订单 insert / 日志 insert / 日志 SUCCESS 更新）；消息日志两库 `status=1`；**单体读副本仍工作**：`GET /api/rank/top10` 返回本次订单号（analytics 经 `OrderQueryAdapter` 读旧库）。
- **决定性判定（本日最值得学的一步）**：改库验读数要选**直通字段**且要**双向**。只改**权威**库（price 888→9999）→ 应用读到 **9999**；再只改**副本**库（→7777）→ 应用**仍读 9999** ✓ 两条一起才成立。
  留档三个"看起来像证据、其实无效"的探针：① 改 `status`（读端做枚举映射，9 显示成 CREATED）；② 插"只存在于一个库"的订单（该表有 `uk_activity_user` 唯一键；且读端对"查不到"返回 `code=0 + QUEUING` 合成对象而非 404）；③ 订单查询带**归属校验**（非本人订单不返回）。最终靠 `information_schema.processlist` 的 `db` 归属 + 双向改值定性。
- **⚠️ 本日最大的坑（已写进代码注释）**：**MyBatis-Plus 自动配置带 `@ConditionalOnMissingBean(SqlSessionFactory)`** —— 一旦为 auth 声明专用工厂，自动配置就整体退让；而未指定 `sqlSessionFactoryRef` 的 `@MapperScan`（order + analytics）会按类型落到**唯一存在的**那个工厂（auth 工厂）→ **服务照常启动、订单查询静默读旧库副本**（不报错不告警）。修法：主工厂 `sqlSessionFactory` 也**显式声明**（约定名）+ 主 `@MapperScan` 显式 `sqlSessionFactoryRef`。并留一行**启动自检日志**（两条数据源 + 每个工厂实际绑定的库）——"配置看着对、连接连错库"这类问题，看自检日志/进程列表比看 yaml 快得多。
- **为什么需要双数据源（结构性原因）**：order 应用要自己的 schema（权威），但 auth-core 的会话校验必须查 `user_session`，而 auth 域**尚未服务化**（表只在旧库）→ 单数据源无解（实测 `Table 'flash_sale_order.user_session' doesn't exist`）。方案：`@Primary` 主数据源 + auth 专用数据源 + 两个 `SqlSessionFactory`（`user.mapper` 绑 auth），**显式可见**（优于在权威库建跨库视图、把耦合藏进 DDL）。取舍：auth 写不参与主数据源事务（单语句写，可接受）；auth 服务化后整类删除；`flash-sale-seckill` 应用用"整库指旧库"的粗办法解决同一问题，届时一并收敛。
- **范围裁剪（诚实记录）**：原计划含"ai 目录改走服务接口"。侦察发现 `AiChatServiceImpl` **还直连两处 mapper**（`ProductMapper` 列表查询 + `SeckillActivityMapper`），完整迁移需新增"活动目录端口" + 内部接口 + 三处适配器实现 = 独立一天；半途迁移无价值（ai 仍读旧库）→ 移入 Day 5。
- **已知取舍**：order 应用仍扫 `analytics.mapper`（`SeckillActivitySnapshotMapper`），而 snapshot 表不在 `flash_sale_order` 里 —— 启动只建 Bean（MyBatis 不校验表存在），该查询路径不在本应用路由上；analytics 服务化那天消掉。
- **门禁与回归**：对账 **14/14 consistent（exit=0）**；全量回归 **131 用例 0 失败 0 错误 BUILD SUCCESS**（1.8 分钟，20 模块）。
- 探针：`P4D4-ProdA` / `P4D4-ActA` + 探针订单保留（旧库 FK 链 订单→活动→商品；也是镜像证据）。今天的额外教训：往旧库 `seckill_order` 插探针行会被 **FK**（activity/product）+ **唯一键 `uk_activity_user`** 双双拦下。
- **收尾时发现的隐藏影响（已处理并留档）**：提交后核对终态，`seckill_message_log` **副本比权威多 23 行** ✗。查明不是脏数据、也不是业务回归：那 23 行是 13:14:49–13:14:52 的一次突发（`consumer_group=seckill:order:group`、全部 `status=1(SUCCESS)`、真实雪花订单号），**但两侧 `seckill_order` 都没有对应订单**（行数 4028 = 4028）—— 即**回归测试跑在单体 JVM 里，其消费者扫到了真实 Redis Stream**，把 23 条已处理消息又消费了一遍、全部被 `uk_activity_user` 判重 → 只留下日志行。
  → **权威切换后的结构性影响**：bootstrap 的集成测试用的是**旧库数据源**，所以**跑一次全量回归就会在旧库（副本）留下"影子消费/影子日志"**，而对账门禁只看两库一致性 → 门禁会红。处置：按 `id > 权威 max(id)` 精确清掉影子行（5175/5175 复原，对账回 **14/14**）；**约定**：回归之后先清影子行再对账。
  → **Day 5 必做**：① **测试数据源隔离**（独立测试库或 Testcontainers）—— 治本；② 或让测试用独立 Stream key，不碰真实 `flash:stream:seckill`；③ CI 里对账的调用顺序（测试 → 清影子 → 对账）。
- **Day 5 清单**：① auth 域服务化（或"瘦用户会话守卫"下沉 infra）→ 删双数据源桥 + seckill 应用切回权威库；② ai 目录端口化（`ProductCatalogPort` 扩"上架列表" + 新建活动目录端口 + 内部接口 + 三处适配器）；③ order→analytics 解耦（`order.created` 事件或端口化）；④ **集成测试数据源隔离**（见上条，否则"回归 → 对账"这条链在权威切换后必然假红）；⑤ **删反向镜像 + 旧库 `flash_sale` 下线**（三阶段迁移收官）。

---

### P4 Day 5 详细任务卡（旧库下线收尾·第一块：ai 摘出旧库 + 测试数据源隔离 + 下线检查表）

> 执行方式：panda 实现 + 导师现场纠偏；预估 3~4 小时。

**先给结论（范围裁剪的理由）**：旧库下线**不是一天能完成的**，它是一条链。本日先做**收益最确定、风险最低**的两块 + 把剩余障碍固化成检查表，而不是硬冲"DROP DATABASE"。

**Day 5 侦察：旧库依赖地图（决策依据）**

- **旧库只剩 7 张表**：`user_account`(111) / `user_session`(127)（auth 域未拆）、`seckill_activity_snapshot`(**0 行**，analytics 的空表)、`product` / `seckill_activity` / `seckill_order` / `seckill_message_log`（**副本**，由三个应用的反向镜像维护）。新 schema 只有 4 张表，**没有 ai 的表** → **ai 服务本身无库**（P2 Day 3 就写着"ai 无库"的目标，本日兑现）。
- **ai 的 DB 用法只有两处**：`LocalProductCatalogAdapter`（`ProductMapper`，P3 Day 5 端口化的产物——但 **ai 内无人注入这个端口**，是历史遗留）、`AiChatServiceImpl.buildCatalog()`（两处直连 mapper：在售商品列表 + 未结束活动列表）。→ 摘掉 DB 的门槛比预想低。
- **商品服务有公开列表端点** `GET /api/products`（还有 `GET /api/products/{id}`）；**活动侧没有内部/公开的"列表"内部端点**，但 `GET /api/seckill/activities`（公开读，seckill 应用✓）正是 ai 需要的"当前场次"数据源 → **ai 走公开端点即可，不新增 API 面**。
- **infra 不依赖 DataSource**（`DualWriteMirror` 用 `DriverManager` 直连；无 mybatis/DataSource 装配）→ ai 去掉 mysql/mybatis-plus 依赖后可以真的没有数据源。
- **测试写库的根因**：`flash-sale-bootstrap` 的测试用主 yaml 的数据源（= 旧库）→ 回归时消费者扫真实 Stream、把结果写进旧库（Day 4 收尾发现的"影子 23 行"）。**修法：给 bootstrap 测试一个独立的测试库快照**。

**剩余障碍（写进检查表，不是本日做）**：① auth 域（2 表 + 4 个运行时宿主 + `@Scheduled` 清理任务归属）；② analytics 的 snapshot 表（**空的**，但 analytics 读端点在单体，经 `OrderQueryAdapter` 读 order 副本）；③ 三个反向镜像；④ Redis 未隔离（测试仍会动真实 Stream）。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 1 ai 端口的骨架 | 新增 `ai.port.CatalogQueryPort`（`onShelfProducts(limit)` / `activeActivities(limit)` + 两个 record）+ `ai.adapter.HttpCatalogQueryAdapter`（复用 `flash-sale-remote` 的 `LoadBalancedClientConfig` 的 LB 客户端，调公开端点） | 端口放**消费方模块**（与 `analytics.OrderQueryPort` 同一约定）；只有 ai 需要 → 适配器留在 ai 内 |
| Step 2 改写目录组装 | `AiChatServiceImpl.buildCatalog()` 改为走端口；删除 `LocalProductCatalogAdapter`；ai POM 去掉 `product-core` / `mybatis-plus` / `mysql`，加 `flash-sale-remote`；启动类去 `@MapperScan`、显式排除 `DataSourceAutoConfiguration`；yaml 删 `datasource` 与 `mybatis-plus` 段 | 工作区先 grep 证明 `ProductCatalogPort` 在 ai 内**无人注入**（否则不能删） |
| Step 3 测试数据源隔离 | 新增 `scripts/create-test-db.ps1`（建 `flash_sale_test` + 7 张表 `LIKE` + `REPLACE ... SELECT` 快照，幂等）；`flash-sale-bootstrap/src/test/resources/application.properties` 覆盖 `spring.datasource.url` 指向测试库 | 7 张表要覆盖单体 `@MapperScan` 的全部面；跑测试前先刷新快照 |
| Step 4 验证 + 回归 + 文档 + 提交 | 起服务判据表（含 **ai 无库**与**停商品服务反证**）→ 全量回归 → **回归前后旧库行数不变** → Day 5 ✅ + 下线检查表 → 提交 | 回退：ai 恢复 datasource/POM 依赖 + 删 test properties |

**验收判据**：① 构建 **20 模块**绿线；② **ai 无库启动**：`:8083` health **不含 `db` 组件**（结构证明：没有 DataSource 就读不到任何库）+ 启动日志无 Hikari/连接池；③ ai 行为不变：`POST /api/support/chat` 空体仍 `40001`（与基线一致）；④ **反证（证明真的走 HTTP）**：停 `:8084` 商品服务 → ai 目录组装**降级**并打印 `AI 商品目录组装失败，降级为空目录`（此前直读 DB 不会有这个降级）；⑤ 结构证据：ai 内无 `Mapper`/`@MapperScan`/`jdbc` 依赖（grep 全清）；⑥ **测试隔离决定性判据**：全量回归绿线 + **回归前后旧库 7 张表行数完全不变**（含 `seckill_message_log`/`seckill_order`）+ 对账 **14/14**；⑦ 其它域不变（products / seckill 活动 / rank / metrics / order 查询）；⑧ 文档：Day 5 ✅ + 旧库下线检查表（剩余 4 块 + 顺序 + 每块前置与验收）。

**坑**：① 去掉数据源必须**显式** `exclude = DataSourceAutoConfiguration.class`（否则无 url 启动即失败）；② 先 grep `ProductCatalogPort` 在 ai 内的使用面再删类（端口存在 ≠ 被使用）；③ 公开端点没有内部令牌保护 → 不要用它做写操作（本日只读 ✓）；④ 测试库快照必须 7 张表齐全（漏表 → 启动/测试报缺表）；⑤ 测试 properties 的键是 `spring.datasource.url`（单体 yaml 用的就是 `url`）；⑥ 快照要幂等（`REPLACE`）+ 数据漂移时重跑；⑦ 回归仍会消费真实 Redis Stream（影子行落在**测试库**，不再污染业务库）→ Redis 隔离列入 Day 6；⑧ ai 无库后 `mybatis-plus`/`mysql` 依赖与 yaml 段要一起删干净（残留会报缺 mapper/无 url）。

**提交计划**：① `refactor(p4-day5): ai 目录改走服务公开接口 + 摘掉数据源（ai 真无库）`；② `test(p4-day5): bootstrap 测试数据源隔离（flash_sale_test 快照）`；③ `docs(p4-day5): plan.md Day 5 完成 + 旧库下线检查表`。

**P4 Day 5 实施记录（2026-10-16）**：

- **改动面**：① ai 新增 `ai.port.CatalogQueryPort` + `ai.adapter.HttpCatalogQueryAdapter`（复用 `flash-sale-remote` 的 LB 客户端，调**公开读接口**：`GET /api/products?page=1&pageSize=N`、`GET /api/seckill/activities`），`AiChatServiceImpl` 两处直连 mapper 改走端口，删除历史遗留的 `LocalProductCatalogAdapter`（ai 内无人注入该端口 → 整类可删）；ai POM 去 `product-core`/`activity-core`/`mybatis-plus`/`mysql`、加 `flash-sale-remote`；启动类去 `@MapperScan`、**收窄 `scanBasePackages`**、`exclude DataSourceAutoConfiguration`；yaml 删 `datasource` 与 `mybatis-plus` 段。② 测试隔离：新增 `scripts/create-test-db.ps1` + bootstrap/auth 两个 `src/test/resources/application.properties` 覆盖 `spring.datasource.url`。reactor **20 模块**、Nacos **8 服务**。
- **判据（全绿）**：`ai` 健康**不再含 `db` 组件**（结构证明：没有 DataSource 就读不到任何库）+ 日志 `HikariPool/jdbc:mysql/SqlSessionFactory` 命中 **0**；chat 基线经网关空体 `40001`；**反证**——seckill 未起/product 在跑时目录只降级**活动**一侧（商品调用成功 !）、停 :8084 后商品一侧也降级（证明确实走 HTTP，而非直读 DB）；其它域（products 列表/详情、seckill 活动、rank、metrics、order 查询）全绿；**全量回归 132 用例 0 失败**（1.7 分钟）；**回归前后 `flash_sale`/`flash_sale_product`/`flash_sale_activity`/`flash_sale_order` 逐表行数完全一致**（测试库 msglog 5175→5198 = 写入确实落在测试库）；对账 **14/14**。
- **⚠️ 本日五个坑（都已修 + 写进代码注释/脚本头）**：
  1. **域库的 `@Service` 会被任何扫描它的运行时实例化**：ai 去掉 `@MapperScan` 后启动即失败 —— `flash-sale-remote` 依赖 `flash-sale-activity-core`（为了端口接口），于是 activity-core 的 `SeckillCacheService` 被扫进 ai，而它要 `SeckillActivityMapper`（ai 已无 mapper）。**修法：收窄 ai 的 `scanBasePackages`**（ai + infra + remote + common + contracts），比"再扫回 mapper"正确 —— 这是"库被多运行时共用"的第二课（第一课是 Day 2 的控制器双份入口）。
  2. **分页响应取值**：`Result<PageResponse<ProductRow>>` 里 `dataOf(...)` 返回的是**页对象**不是 list（编译期 `不兼容的类型`）→ 取 `.getRecords()` 并防 null。
  3. **`LocalDateTime` 反序列化必须带 `@JsonFormat`**：上游 VO 用 `yyyy-MM-dd HH:mm:ss` 序列化，客户端 record 不加注解会按 ISO 解析而抛 `DateTimeParseException`（记录型 DTO 上同样要标）。
  4. **`LIKE` 建表丢外键 → 依赖 FK 的失败路径用例"意外成功"**：`SeckillMessageReliabilityTest` 的"坏活动/坏商品"路径正是靠 `seckill_order` 的 FK 失败制造 FAILED/DEAD；测试库没有外键时这两条直接 SUCCESS（`expected: 2 but was: 1`）。**修法：快照脚本从 `information_schema` 生成 `ALTER TABLE ... ADD FOREIGN KEY`（含原 ON DELETE/UPDATE 规则）在数据导入后重建**（实测恢复 5 条后 3/3 转绿）。
  5. **快照不能只 `REPLACE`**：`REPLACE INTO ... SELECT` 不清上一轮遗留行（实测残留 23 行导致断言漂移）→ 改为 **DROP + CREATE + INSERT**。
  另外两个小坑：**PowerShell 5.1 按 ANSI 读无 BOM 的 `.ps1`** → 中文注释被判成乱码致解析失败（脚本必须存 UTF-8 **带 BOM**）；**测试隔离要覆盖到 auth 应用**（`AuthRegisterIntegrationTest` 会真的注册用户，业务库 `user_account` 从 111 涨到 115 —— 已给 auth 的 test properties 加同样的覆盖）。
- **诚实说明**：① Redis 仍是共享的 —— 测试依旧会消费真实 Stream（只是结果写进测试库），隔离 Redis 列入 Day 6；② 业务库 `user_account` 里累计着历史测试账号（111 行里相当一部分，含本次隔离前那 4 行）；③ 本环境 LLM 不可达，chat 带正文返回 `50301`（与 Day 4 行为一致，非本次回归）；④ 旧库下线**尚未完成**，本日只摘掉 ai 这一块 + 铺好门禁。

**旧库下线检查表（Day 6+ 的路线，按依赖顺序）**：

| 顺序 | 剩余障碍 | 前置/解锁 | 验收要点 |
|---|---|---|---|
| ① | **auth 域「库」拆分**（`user_account`/`user_session` 2 表；4 个运行时都宿主 auth-core；`UserSessionService` 的 `@Scheduled` 清理任务归属要一并收口） | — | 新 schema + 迁移 + 反向镜像；写者唯一性核查（谁在删过期会话）；order 双数据源桥与 seckill 应用数据源随之删除 |
| ② | **analytics 读端点跨服务化**（snapshot 表是**空的**，但 `OrderQueryAdapter` 仍读 order 副本） | ①（auth 桥拆除后单体才可能脱离旧库） | rank/metrics 从「读 DB 副本」改为读事件/端口；order→analytics 边断开 |
| ③ | **Redis 隔离**（测试不再动真实 Stream） | 可并行 | Testcontainers 或独立 database 索引 |
| ④ | **删反向镜像 + 旧库 `flash_sale` 下线**（三阶段迁移收官） | ①②③ 全部完成 | 三个应用的 `module.db.dual-write` 段删除、`flash_sale` 停写停读、对账脚本退役 |

---

### P4 Day 6 详细任务卡（旧库下线检查表·第 ① 项：auth 域「库」拆分 + 清理任务归属收口）

> 执行方式：panda 实现 + 导师现场纠偏；预估 3~4 小时。

**目标**：把 auth 域两张表（`user_account` / `user_session`）迁到独立 schema `flash_sale_auth` —— **权威 = 新 schema**（auth 应用读写），**副本 = 旧库 `flash_sale`**（monolith / seckill / order 三个运行时继续用它做会话校验），由**反向镜像**保鲜。与 product/activity/order 完全同法，本日只做 auth。**顺带修一个潜伏 bug**：过期会话清理任务的 `@Scheduled` 写在共享库里，被 3 个运行时同时触发。

**侦察结论（决定可行性与动作面）**：

- **单写者前提成立** ✓：两张表的全部写方法只在 `UserAccountService`（`insert` / `updateById` ×2 / `revokeAllForUser` ×3）与 `UserSessionService`（`create` / `revoke` / `rotateCsrf` / `purgeExpiredSessions`）里，而这些方法只被 **auth 应用**的 `UserAuthController` / `AdminUserController` 调用。另外三个宿主的 `ApiAccessInterceptor` **只读**（`authenticate` 查会话 + `requireCsrf` 只比对哈希，不写）→ **其它运行时能触达的写点 = 0** ✓（与 Day 1 切读、Day 4 order 同形）。
- **两表列（旧库实测）**：`user_account`(id, account_name, password_hash, display_name, status, created_at, updated_at, deleted_at) **115 行**，UK `account_name`；`user_session`(**主键是 `token_hash`**（char(64)，无自增 id）, user_id, csrf_token_hash, expires_at, revoked_at, created_at) **133 行**；两表间 1 条外键 `fk_session_user`（ON DELETE NO ACTION）。
- **⚠️ 清理任务是本日唯一的多写者**：`UserSessionService.purgeExpiredSessions()` 带 `@Scheduled(cron = "0 17 3 * * *")`，而"带 `@EnableScheduling` **且**宿主 auth-core"的有 **3 个运行时**（monolith / order 应用 / seckill 应用）→ 每天 03:17 三方各自 `DELETE` 一次。今天三方都指旧库（同表、幂等 → 无害）；**拆分后就会变成"非属主去删副本"** → 必须随本次一起收口（`activity` 应用也有 `@EnableScheduling`，但它不带 auth-core，不受影响）。
- **auth-core 已依赖 infra** ✓（`DualWriteMirror` 可直接用，与 `order-core` 同先例）；**auth 应用没有 `@EnableScheduling`** ✓ → 收口时一并加上。

**设计决定（写给评审）**：

1. **迁移带上那条外键**（与前三块拆分的"零外键"不同）：`user_session.user_id → user_account.id` 是真实不变量（不存在无主会话），auth 的写者只有一个应用、迁移可"先用户后会话"→ 带上更安全。Day 5 的教训（测试库丢外键 → 失败路径用例静默成功）也支持这个方向。
2. **调度入口按"文件位置表达归属"**（沿用 Day 3 `StreamConsumerScheduler` 的手法）：把 `@Scheduled` 从 auth-core 的 `UserSessionService` **移到 auth 应用**的新类 `user.scheduler.AuthSessionCleanupScheduler`；清理逻辑 `purgeExpiredSessions()` 留在库里（可测），调度器只做触发 + 两侧清理。并加**守卫测试**断言该类不在单体 classpath（与 Day 3 同形）。
3. **清理要两侧都清**：拆分后权威在 `flash_sale_auth`，副本（旧库）的过期行若不一起清会长期堆积 → `AuthMirror.purgeExpired(cutoff)` 提供"副本侧同样的 DELETE"，调度器先删权威、再删副本。
4. **不改另外三个运行时**：它们继续读旧库副本 ✓ 是设计；但要**主动验证**（判据 ⑤）。
5. **修正一处 Day 5 笔记的口径**：auth 库拆分**并不能**直接"解锁删 order 双数据源桥"—— 拆完后 auth 两表在 `flash_sale_auth`，仍不在 `flash_sale_order` 里，桥照样要。真正删桥的前置是**会话校验不再需要各运行时自带 auth mapper**（内部 `validate` 接口 / 会话守卫下沉），那是下一块。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 1 DDL + 迁移 | `db/migration/V20261016__split_auth_schema.sql`：建库 + 两表（`LIKE`）+ **补外键** + 全量 `REPLACE`（先用户后会话）+ 行数/外键校验 | **停服务时跑**；幂等（先 DROP 再建） |
| Step 2 镜像组件 | `DualWriteProperties` += `legacyAuthUrl`（键 `legacy-auth-url`）；`DualWriteMirror` += `mirrorAuth`；auth-core 新增 `user.mirror.AuthMirror`（account 按 id upsert / session 按 token_hash upsert / `revokeAllForUser` / `purgeExpired`） | 复用既有 `mirror()` 内核（日志已带目标库名 ✓） |
| Step 3 写点接镜像 | `UserAccountService`（`create` / `adminUpdate` / `softDelete`）+ `UserSessionService`（`create` / `revoke` / `rotateCsrf`），共 6 处 | 镜像**默认关** → 单体/测试零行为变化 |
| Step 4 清理归属收口 | 删 `UserSessionService` 上的 `@Scheduled`；新增 auth 应用 `user.scheduler.AuthSessionCleanupScheduler`（先删权威、再镜像删副本）；auth 启动类加 `@EnableScheduling`；守卫测试 | 本日"顺带修的潜伏 bug" |
| Step 5 切权威 | auth 应用 yaml：datasource → `flash_sale_auth`；`module.db.dual-write`（`enabled=true` + `legacy-auth-url`） | monolith / seckill / order **不动** |
| Step 6 对账扩展 | `reconcile-product-activity.ps1` 增 **2 项 auth 检查**（行数 + 指纹；`user_session` 指纹以 `token_hash` 为准） | 迁移前后各跑一次 |
| Step 7 验证 + 回归 + 文档 + 提交 | 8 服务判据表（含权威/副本双向证据 + 清理归属）→ 回归 → Day 6 ✅ → 提交 | 回退 = datasource 换回旧库 + 关镜像 |

**验收判据**：① 构建 **20 模块**绿线；② Nacos **8 服务** + `:8082` health `db` UP（连 `flash_sale_auth`）；③ **迁移门禁**：迁移前后对账一致（14 → **16 项**）；`flash_sale_auth` 两表行数 = 旧库 = **115 / 133** ✓ 且外键 **1 条** ✓；④ **权威在 auth schema**：经网关 注册→登录→登出 全通；新用户在 `flash_sale_auth` 命中 ✓ + 镜像日志 `target=auth(db=flash_sale)` ✓ + **旧库副本同有** ✓；⑤ **另外三个运行时读副本仍工作**：用会话经网关做一次**秒杀**（monolith/seckill/order 的会话校验走旧库副本）+ 一次 `/api/seckill/users/me/orders` → 正常；⑥ **清理归属**：守卫断言 `AuthSessionCleanupScheduler` 不在单体 classpath；手工触发一次清理 → **权威与副本两侧同时少掉过期行**（造一行过期会话验证）；⑦ 路由与反证：停 `:8082` → 经网关 `/api/auth/login` **503** 而 `/api/seckill/activities` 仍 **200**；⑧ 对账 **16/16** consistent；⑨ 其它域不变（products / rank / metrics / order / ai）；⑩ 全量回归绿线 + 文档（Day 6 ✅ + 检查表推进）。

**坑**：① 迁移必须在**停服务**时跑（避免半程写入）；② `user_session` **主键是 `token_hash`**（无 id）→ 指纹/幂等一律以它为准；③ 新 schema 首次建无所谓，但脚本**重跑**前必须 DROP（`REPLACE` 不删旧行，Day 5 教训）；④ 镜像 SQL 要**覆盖全部列**（`ON DUPLICATE KEY UPDATE`），尤其 `user_account` 的 `deleted_at` 与 `user_session` 的 `revoked_at` 可为 NULL；⑤ 镜像默认关 → 忘了在 auth 应用 yaml 开 `enabled` 会让副本凝固（判据 ④ 专抓）；⑥ `UserSessionService` 上的 `@Scheduled` **必须真的删掉**（否则收口无效，判据 ⑥ 抓）；⑦ 外键在**镜像写副本**时受顺序约束（先 user 后 session ✓ 现状天然满足）；⑧ 单体 / order / seckill **不要动**（读副本即设计）；⑨ 前端 `SESSION_COOKIE` 逻辑与本日无关（会话表结构不变）。

**提交计划**：① `feat(p4-day6): auth 独立 schema + 幂等迁移 + AuthMirror + 清理任务归属收口`；② `test(p4-day6): 清理调度归属守卫 + 对账扩到 16 项`；③ `docs(p4-day6): plan.md Day 6 完成 + 旧库下线检查表推进`。

**P4 Day 6 实施记录（2026-10-16）**：

- **改动面**：① 迁移 `db/migration/V20261016__split_auth_schema.sql`（建 `flash_sale_auth` + 两表 `LIKE` + **补外键** + 全量 `REPLACE`（先用户后会话）+ 校验；已执行：**115/115、133/133、外键 1 条**）；② 镜像组件：`DualWriteProperties` += `legacyAuthUrl`、`DualWriteMirror` += `mirrorAuth`、auth-core 新增 `user.mirror.AuthMirror`（account 按 id upsert / session 按 **token_hash** upsert / 单会话吊销 / 按用户批量吊销 / CSRF 轮换 / 过期清理）；③ **6 个写点接镜像**：`UserAccountService.create` / `adminUpdate`（状态与密码两个分支各吊销一次）/ `softDelete`（账号 + 全量会话吊销），`UserSessionService.create` / `rotateCsrf` / `revoke`；④ **清理归属收口**：删掉 `UserSessionService.purgeExpiredSessions()` 上的 `@Scheduled`（逻辑保留并改为"权威 + 副本两侧清理"），新增 **flash-sale-auth** 的 `user.scheduler.AuthSessionCleanupScheduler`（cron 原样 03:17）+ auth 启动类加 `@EnableScheduling`；⑤ auth 应用 datasource → `flash_sale_auth` + `module.db.dual-write`（enabled=true + legacy-auth-url）；⑥ 对账 14 → **18 项**（+`auth.account_rows` / `auth.account_tail_fp` / `auth.session_rows` / `auth.session_tail_fp`）。
- **判据（全绿）**：Nacos **8 服务**、`:8082` health `db` UP 且 `information_schema.processlist` **出现 `flash_sale_auth` 连接**；迁移门禁 **18/18**；**权威/副本双向**：注册→登录→`/api/auth/me`→CSRF 轮换→登出 全通，新用户权威命中 + **副本命中=1**、CSRF 哈希**两侧一致**、吊销**两侧各 1**，镜像日志 `target=auth(db=flash_sale)` **×5**（建号/建会话/登录/轮换/吊销，账目正好对得上）；**另外三个运行时读副本仍工作**：`/api/seckill/users/me/orders`（order 应用，会话校验读旧库）`code=0`；**路由反证**：停 `:8082` → `auth/login` **503** 而 `seckill/activities` **200**；其它域 products/rank/metrics 正常；**全量回归 136 用例 0 失败**（1.8 分钟）；**旧库签名回归前后逐字段一致**（`user=116 session=135 order=4028 msglog=5175 product=9`）；回归后再对账仍 **18/18**。
- **⚠️ 本日四个坑**：
  1. **测试必须关镜像**：`legacy-auth-url` 是**硬编码旧库**、不随测试数据源走 —— auth 的集成测试若开着镜像，注册用例会把测试账号镜像进**业务库**（正是 Day 5 刚花一天消灭的那类污染）。修法：`flash-sale-auth/src/test/resources/application.properties` 显式 `module.db.dual-write.enabled=false`（测试不是副本的维护者）。
  2. **javadoc 别插到 `*/` 之后**：给启动类补说明时把 `* ...` 段落写到注释块外 → 中文全角标点直接变成 Java 非法字符（`非法字符: '\uff0c'`）。教训：改启动类注释要落在注释内。
  3. **构造参数变化会打穿单测**：`UserAccountService` 多一个 `AuthMirror` 依赖 → `UserAccountServiceTest` 的 `new UserAccountService(...)` 编译失败（好在编译期就暴露，不是运行期）。
  4. **`user_session` 主键是 `token_hash`**（char(64)，**没有自增 id**）：指纹、幂等键、排序一律以它为准；且权威的 `deleteExpired` 条件是 `expires_at < ? OR revoked_at < ?`（**两列都清**）→ 镜像的那条 DELETE 必须与权威**同形状**，否则两侧行数会漂移。
- **一处口径修正（诚实记录）**：Day 5 笔记把"auth 库拆分"写成能"解锁删 order 双数据源桥"，**不准确** —— 拆完后 auth 两表在 `flash_sale_auth`，仍不在 `flash_sale_order` 里，桥照样要。真正删桥的前置是**会话校验不再需要各运行时自带 auth mapper**（内部 `validate` 接口 / 会话守卫下沉），已写进检查表。
- **设计决定留档**：迁移**带上 `fk_session_user`**（与前三块拆分的"零外键"不同）—— 它是真实不变量（无主会话不存在），且 auth 写者唯一、迁移可先用户后会话；Day 5 的教训（丢外键会让失败路径静默成功）也支持这个方向。
- **诚实说明**：① 清理的"两侧都清"由**单测**（`UserSessionServiceTest`：调 mapper 删权威 + 调 mirror 删副本）+ **结构守卫**（共享库服务不得带 `@Scheduled`；单体 classpath 无调度类）覆盖，**没有**等到 03:17 的 cron 实跑（避免为了验证改生产节奏）；② 前三个运行时的会话守卫**没动代码**，只做行为验证（判据⑤）；③ 旧库下线**仍未完成**，检查表剩 3 项。

---

### P4 Day 7 详细任务卡（旧库下线检查表·第 ① 项：会话校验跨服务化 → 拆掉 order 双数据源桥）

> 执行方式：panda 实现 + 导师现场纠偏；预估 3~4 小时。

**目标**：让 **monolith / seckill / order 三个运行时不再需要 auth 的 mapper** —— 会话校验（`ApiAccessInterceptor`）改为调用 auth 应用的**内部接口**。做完这一刀才能真正删掉：① order 应用的双数据源过渡桥（`order.config.DataSourceConfig` + `module.auth-db`，Day 4 的临时结构）；② seckill 应用"整库指旧库"的粗办法；③ 三处 `@MapperScan` 里的 `user.mapper`。

**侦察结论（Day 6 完成后）**：

- **唯一碰库的缝隙极小** ✓：`ApiAccessInterceptor.requireUser/requireSessionUser` 只调 `userSessionService.authenticate(request)`（查会话 + 查账号是否有效）与 `requireCsrf(request, session)`（**只比对哈希，不写**）。`UserTokenRegistry`（旧 `X-User-Token` 兼容路径）是**纯内存 Map**，不碰库 ✓。
- **三个运行时的业务代码只读 request attribute** ✓（`AUTHENTICATED_USER_ID`），没有任何一处直接注入 `UserAccountService` / `UserSessionMapper` → 会话校验一旦跨服务化，它们对 auth 的**编译期依赖可以整体消失**。
- **内部接口范式现成** ✓：infra 的 `InternalAccessWebConfig` 对 `/internal/**` 校验 `X-Internal-Token`（fail-closed，`module.internal.token`）；控制器模板 `InternalSeckillStockController`、调用方模板 `HttpSeckillStockAdapter`（LB 客户端 + 内部令牌 + `Result<T>` 解包）。
- **依赖面**：`flash-sale-remote` 已被 activity / ai / seckill 依赖；monolith 与 order 需要新增（monolith 本来就有 activity-core，无新增域库）。
- **写侧不动**：`UserAccountService`（注册/改资料/软删）与 `UserSessionService` 的写方法只被 **auth 应用**的控制器调用 ✓ → 它们留在 auth 应用（**用开关收敛**）。

**设计（关键：用"模式开关"表达部署角色）**：

| 角色 | `module.auth.mode` | 生效的 Bean | 数据源 |
|---|---|---|---|
| **auth 应用**（权威） | `db` | `LocalSessionValidationAdapter` + 账号/会话写服务 | `flash_sale_auth` |
| **其余运行时**（校验方） | 不配（默认 `remote`） | `HttpSessionValidationAdapter` | 各自权威库（不再需要 auth 表） |

- 新增端口 `user.port.SessionValidationPort`（`validate(tokenHash) -> Optional<SessionView(userId, csrfTokenHash)>`）；
- `user.web.SessionGuard`（auth-core，**无 mapper、所有运行时都有**）：cookie 解析 + 端口校验 + CSRF 比对 → 拦截器改用它；
- 账号/会话的**写服务加 `@ConditionalOnProperty(module.auth.mode=db)`** —— 默认不存在（与镜像 `enabled` 同一哲学：危险能力必须显式声明）；
- 内部接口：auth 应用 `POST /internal/auth/sessions/validate`（`X-Internal-Token`，请求体 `{tokenHash}`，响应 `{userId, csrfTokenHash}` 或空 data 表示无效）；
- **失败语义**：验不了 ≠ 没登录 —— 传输失败/非 0 业务码一律 **fail-closed**（拒绝并回可诊断的错误码），绝不"放行"。

| 步骤 | 内容 | 要点 |
|---|---|---|
| Step 1 端口 + 本地适配器 | `SessionValidationPort` + `LocalSessionValidationAdapter`（`@ConditionalOnProperty` db）+ `SessionGuard` | 本地适配器 = 原 `authenticate` 的库逻辑（会话 + 账号有效） |
| Step 2 HTTP 适配器 | `flash-sale-remote` 的 `HttpSessionValidationAdapter`（默认 remote） | 照抄 `HttpSeckillStockAdapter`；失败 fail-closed |
| Step 3 内部接口 | auth 应用 `InternalSessionController`（`/internal/auth/sessions/validate`） | 复用 infra 的 `/internal/**` 令牌护栏；调端口（db 模式下即本地查库） |
| Step 4 拦截器改道 | `ApiAccessInterceptor` 的 `UserSessionService` → `SessionGuard` | 写侧方法签名不变；`AUTH_SESSION_ATTRIBUTE` 沿用 |
| Step 5 写服务开关化 | `UserAccountService` / `UserSessionService` 加 `@ConditionalOnProperty("module.auth.mode"="db")` | 三个运行时的上下文里不再需要 user.mapper |
| Step 6 拆桥 | 删 `order.config.DataSourceConfig` + `module.auth-db` 段；monolith/seckill/order 三处 `@MapperScan` 去掉 `user.mapper`；seckill 应用 datasource → `flash_sale_activity`；auth 应用 yaml 加 `mode: db` | order 应用回到"单数据源 + 单工厂"，Day 4 的启动自检随之删 |
| Step 7 验证 + 回归 + 文档 + 提交 | 判据表 → 回归 → Day 7 ✅ | 回退 = 三个 yaml 恢复 mode/datasource + 还原 DataSourceConfig |

**验收判据**：① 构建 20 模块绿线；② Nacos 8 服务 + 各应用健康；③ auth 全流程经网关可走（注册/登录/me/csrf/登出，此时校验走**本地**适配器）；④ **跨服务校验成立**：带会话经网关做订单查询 → 由 order 经 HTTP 调 auth 通过；⑤ **决定性反证**：停 `:8082` → 同一带会话请求**被拒绝**（证明确实改为跨进程校验，而不是仍读本地库）；⑥ **结构证据**：三处 `@MapperScan` 无 `user.mapper`、`order.config.DataSourceConfig` 已删、order 启动自检只有**一条数据源 + 一个工厂**；⑦ 对账 **18/18**（auth 副本仍由 auth 应用维护）；⑧ 其它域不变（products / rank / metrics / ai）；⑨ 全量回归绿线（含改签名后的 `ApiAccessInterceptorTokenTest`）；⑩ 文档：Day 7 ✅ + 检查表推进到剩 2 项。

**坑（预判）**：① 拦截器在**每个请求**上调用 → 无会话时（无 Cookie）必须**短路**，不能先去问 auth 服务（否则未登录请求也打一次网络）；② 失败必须 fail-closed（放行 = 认证旁路，是本日最高危的错误形态）；③ 共享库类型引用 vs Bean 存在：客户端模式下 `UserSessionService` 类仍在 classpath（拦不到 Bean 才是关键），测试若直接注入写服务会拿不到 Bean；④ `UserAccountService` 也被 auth 应用的 `/api/auth/me` 等路径用 → 开关只能加在"写侧服务"，不能让 auth 应用缺 Bean；⑤ 删双数据源桥后 order 应用只剩一个 `SqlSessionFactory` → 自动配置的 `@ConditionalOnMissingBean` 逻辑随之回到"显式声明"那条路（Day 4 的坑要复查）；⑥ seckill 应用切 `flash_sale_activity` 后要复验活动读端点（副本 → 权威）；⑦ 回归里若有"带会话的集成测试"，需要改为 mock 端口或开启 db 模式。

**提交计划**：① `refactor(p4-day7): 会话校验跨服务化（SessionValidationPort + 内部 validate 接口 + 拦截器改道）`；② `refactor(p4-day7): 拆 order 双数据源桥 + 三处 user.mapper 出扫描面`；③ `docs(p4-day7): plan.md Day 7 完成 + 检查表推进`。

**P4 Day 7 实施记录（2026-10-16）**：

- **改动面**：① `contracts` 新增 `SessionValidationPort` + `SessionValidationRequest`（**端口放契约层**，不能放 auth-core —— 否则 `flash-sale-remote` 要反依赖 auth-core，会把 `ApiAccessWebConfig` 等 Web 装配顺带装到 ai/activity/product 上）；② auth-core 新增 `LocalSessionValidationAdapter`（`module.auth.mode=db`）+ `SessionGuard`（**无 mapper**，cookie 解析 + 端口校验 + CSRF 比对）；③ `flash-sale-remote` 新增 `HttpSessionValidationAdapter`（默认 remote，LB + `X-Internal-Token`，**fail-closed**）；④ auth 应用新增 `InternalSessionController`（`POST /internal/auth/sessions/validate`）+ yaml 补 `module.internal.token`（服务方）与 `mode: db`；⑤ `ApiAccessInterceptor` 改依赖 `SessionGuard`；⑥ `UserAccountService`/`UserSessionService` 加 `@ConditionalOnProperty(module.auth.mode=db)`；⑦ **拆桥**：删 `order.config.DataSourceConfig` + `module.auth-db` + 三处 `user.mapper` 扫描 + order `jdbc-url`→`url` + order 补内部令牌 + seckill 数据源切 `flash_sale_activity` + 单体/order 引入 `flash-sale-remote`。
- **判据（全绿）**：构建 20 模块；Nacos **8 服务**；`login`/`/auth/me`（auth 本地校验）✓；**④ 跨服务校验**——seckill 应用（**已无 user.mapper**、数据源已切权威库）带会话执行秒杀 → `40001 活动已结束`（= 认证通过），无会话 → `40100`；order 应用带会话查订单 → `code=0`；**⑤ 决定性反证**——停 `:8082` → 同一带会话请求 `50000`「**会话校验服务不可用：I/O error on POST request for "http://flash-sale-auth/internal/auth/sessions/validate"**」（**文字级证明真的跨进程 + fail-closed**，日志同时命中 fail-closed 标记）；**⑥ 结构证据**——三处 `@MapperScan` 无 `user.mapper`、`order.config.DataSourceConfig` 已删、order 启动日志不再有"数据源自检"与 auth 数据源；⑦ 对账 **18/18**；⑧ 其它域 products/seckill 列表/rank/metrics 正常；⑨ **回归 136 用例 0 失败**（1.8 分钟）；⑩ 文档。
- **⚠️ 本日最贵的坑（"域库 Bean 被非宿主运行时扫到"第三次现身）**：单体与 order 为了拿 HTTP 适配器引入 `flash-sale-remote`，而 remote 依赖 `activity-core`（为端口接口）→ ① 单体出现**两个 `ProductCatalogPort`**（自己的 `LocalProductCatalogAdapter` + remote 的 `HttpProductCatalogAdapter`）→ 启动失败；② order 被扫进 `SeckillCacheService`/`SeckillActivityServiceImpl`（要 `activity.mapper`）→ 启动失败。**修法**：单体给本地实现标 `@Primary`（保留自身行为）；order **收窄 `scanBasePackages`**（order/analytics/user/infra/remote，与 Day 5 ai 同法）；`SeckillCacheService` 加 `module.activity.cache-service` 能力开关（order 关掉）。教训固化：**"引入一个共享模块"等于"引入它整条依赖链上的所有 Bean"**，跨模块装配必须显式声明扫描面。
- **一处必须同时改的细节**：`jdbc-url` → `url`。Day 4 的桥用手写 `DataSourceBuilder` + `@ConfigurationProperties`，键名可以是 `jdbc-url`；桥删除后回到 Boot 自动配置，只认 `url` —— 不改会直接"Failed to configure a DataSource"（与 Day 5 ai 的坑同源）。
- **失败语义（本日最重要的设计点）**：验不了 ≠ 没登录。传输失败/业务码非 0 一律 **fail-closed 抛错**（50000「会话校验服务不可用」，日志 ERROR），而"查无此会话"才返回 empty（→ 40100 请先登录）；两者若混同，排障时会把"依赖挂了"误读成"用户没登录"，更糟的是任何一种"异常即放行"都等于全站认证旁路。
- **诚实说明**：① 内部校验接口**每请求一次网络调用**（无缓存）—— 单机内网可接受，Redis 会话副本/短 TTL 缓存列入后续（会牺牲"吊销即时生效"，需权衡）；② `ApiAccessInterceptorTokenTest` 已改签名；回归里没有"带会话的集成测试"，所以没有额外 mock 工作；③ 旧库下线**仍未完成**，检查表剩 2 项（analytics 跨服务化、Redis 隔离）。

---

### P4 Day 8 详细任务卡（旧库下线检查表·第 ② 项：analytics 读端点跨服务化）

> 执行方式：panda 实现 + 导师现场纠偏；预估 2~3 小时。

**目标**：把**单体侧** rank / metrics 的订单数据来源从"读旧库 order 副本"改为**调 order 服务**。这是旧库最后一条**读业务表**的边（做完之后：旧库只剩"三个写侧副本 + 一张被单体独占的 snapshot 表"）。

**侦察结论**：

- analytics 已独立成模块 `flash-sale-analytics` ✓，端口 `analytics.port.OrderQueryPort`（`orderNosOf(activityId, userIds)` → userId→orderNo 映射、`countByActivity(activityId)` → 计数）由 order 域实现 `order.adapter.OrderQueryAdapter`（**类在 order-core，行为随宿主的 datasource 变**）：
  - 在 **order 应用**：读权威 `flash_sale_order` ✓（正确，保留）；
  - 在 **单体**：读旧库 `flash_sale` 的 order 副本 ✗ → 本日要切断的就是这条。
- 消费者只有两个：`SeckillRankServiceImpl.topN`（榜单补订单号）与 `MetricsServiceImpl.collectMetrics`（订单计数）。`MetricsServiceImpl.captureSnapshot` 另写 `seckill_activity_snapshot`（单体/admin 手动触发；表在旧库、且是空表）→ **不在本日范围**，作为"旧库最后一件遗留物"单独记录（见诚实说明）。

**设计（与 Day 7 同法：用模式开关表达部署角色）**：

| 角色 | `module.analytics.order-query` | 生效实现 |
|---|---|---|
| **order 应用**（数据属主） | 不配（默认 `local`） | `order.adapter.OrderQueryAdapter`（查自己的权威库） |
| **单体**（跨进程读） | `remote` | `analytics.adapter.HttpOrderQueryAdapter`（bootstrap 内，调 order 的内部接口） |

- 新增 order 应用内部端点：`POST /internal/orders/order-nos`（体 `{activityId, userIds}` → `{userId: orderNo}`）与 `GET /internal/orders/activities/{activityId}/count` → 都委托给 `OrderQueryPort`（本地实现 ✓ 与 Day 7 的内部会话接口同一手法）；**鉴权复用 infra 的 `/internal/**` + `X-Internal-Token`**（order 应用 yaml 已有令牌 ✓）。
- 失败语义：同 Day 7 —— **fail-closed**，校验不了/调不通就报错，绝不"返回 0 条订单"（那会让榜单静默变空、指标失真，比报错更难查）。

| 步骤 | 内容 |
|---|---|
| Step 1 | `contracts` 新增内部请求契约 `OrderNosRequest`；order 应用新增 `InternalOrderController`（两个端点，委托端口） |
| Step 2 | 单体新增 `analytics.adapter.HttpOrderQueryAdapter`（`module.analytics.order-query=remote` 才生效，LB + 内部令牌 + `Result` 解包 + fail-closed） |
| Step 3 | `OrderQueryAdapter` 加 `@ConditionalOnProperty(..., havingValue="local", matchIfMissing=true)`；单体 yaml 配 `order-query: remote` |
| Step 4 | 验证：rank/metrics 经网关正常 → **反证**：停 order 应用 → 两者报"订单查询服务不可用"（文字级证明跨进程）+ 无会话/无管理员令牌的路径不受影响 |
| Step 5 | 回归 + 文档 + 提交 |

**验收判据**：① 构建 20 模块绿线；② 8 服务 + Nacos 8；③ **rank/metrics 走跨进程**：`/api/rank/top10` 与 `/api/admin/.../metrics` 正常且结果与 Day 7 一致（订单计数用**权威库**核对）；④ **决定性反证**：停 `:8087` → rank/metrics 返回"订单查询服务不可用"（拒绝而非静默 0）；⑤ order 应用自身订单查询不受影响（它用本地实现）；⑥ 对账 **18/18**；⑦ 全量回归绿线；⑧ 文档（Day 8 ✅ + 检查表剩 1 项 + snapshot 遗留物记录）。

**坑**：① `Map<Long,String>` 的 JSON key 只能是字符串 → 客户端要显式把 `userId` 转 `Long`（不要用 `Map<Long,String>` 直接反序列化，用 `Map<String,String>` 再转换）；② 端点是**内部接口**：不能走网关暴露（`/internal/**` 已在 infra 的护栏里，且网关只路由 `/api/**`）；③ `analytics` 模块**不加** web 依赖 —— HTTP 适配器放单体（bootstrap 已有 remote/infra ✓），避免把 web 栈拖进域模块；④ order 应用**不要**配 `order-query`（保持默认 local ✓）；⑤ metrics 的 snapshot 写入路径在单体仍连旧库 ✓ 本日不动，但要在文档里留话。

**提交计划**：① `refactor(p4-day8): analytics 订单读路径跨服务化（OrderQueryPort 的 HTTP 实现 + order 内部端点）`；② `docs(p4-day8): plan.md Day 8 完成 + 检查表推进`。

**P4 Day 8 实施记录（2026-10-16）**：

- **改动面**：① `contracts` 新增 `OrderNosRequest`；② order 应用新增 `InternalOrderController`（`POST /internal/orders/order-nos`、`GET /internal/orders/activities/{id}/count`，均委托 `OrderQueryPort` 本地实现，复用 infra 的 `/internal/**` 令牌护栏）；③ **单体**新增 `analytics.adapter.HttpOrderQueryAdapter`（`module.analytics.order-query=remote` 才生效：LB + 内部令牌 + `Result` 解包 + **fail-closed**）；④ `order-core` 的 `OrderQueryAdapter` 加 `@ConditionalOnProperty(..., havingValue="local", matchIfMissing=true)` + 说明"同一个类在不同宿主体内语义不同"；⑤ 单体 yaml 配 `module.analytics.order-query: remote`；order 应用**不配**（默认 local = 查自己的权威库）。
- **判据（部分实证）**：① 构建 **BUILD SUCCESS**（20 模块）✓；② 4 个必需服务（单体/order/gateway/auth）起 ✓；③ rank/metrics `code=0`，metrics `orderCount=1` 与权威库 `flash_sale_order` 计数一致（旧库副本也是 1，故计数本身不具区分性，见④）✓；④ **决定性反证**：停 `:8087` → metrics `50000 订单查询服务不可用：I/O error on GET request for "http://flash-sale-order/internal/orders/activities/90173201268736/count"` + 单体日志 fail-closed 命中 ✓✓（与 Day 7 同形的文字级跨进程证明）；⑤ order 应用自身订单查询未受影响（未改其装配）✓。
- **收尾回收（同日完成）**：① **全量回归 136 用例 0 失败**（2.3 分钟）—— 但**首次是红的**：`SeckillMetricsTest`(6) + `SeckillRankTest`(4) 共 **10 个 error**，根因正是本卡预登记的坑（bootstrap 测试是单进程上下文、没有 order 服务在跑 → 新 HTTP 适配器 fail-closed）；修法：**测试属性 `module.analytics.order-query=local`**（测试库里有 order 两张表，等价口径），代价是"跨进程分支不被集成测试覆盖"，由运行时反证承担 ✓；② 业务库 + 四权威 schema **回归前后逐表不变**（`116/139/4028/5175 | 9 | 23 | 4028/5175 | 116/139`）✓；③ 对账 **18/18** ✓；④ **rank 分支**：探针失败的根因找到 —— 单体 Redis 用 **`database: 1`** 而 RESP 探针写进了 db 0（同时也暴露第一次 `-ERR Protocol error: invalid bulk length` 是 PowerShell 转义写错）；改用**直接打内部端点**做判别：`GET /internal/orders/activities/{id}/count` → `code=0 data=1`、`POST /internal/orders/order-nos`（体 `{activityId, userIds:[90173202427904]}`）→ `code=0 data={"90173202427904":"SK202610161302501540000"}`（**正是权威库里那笔订单**）、**无令牌 → `40100`**（内部护栏 fail-closed）✓✓ ⇒ **服务端与契约侧已钉死**；客户端 `order-nos` 分支（POST + key 还原 Long）仍未被运行时覆盖，与已证实的 `count` 分支同属一个适配器/同一包装，**如实留作未覆盖项**。
- **本日门禁暴露的第二个潜伏 bug（Day 5 的修复引入）**：`scripts/create-test-db.ps1` 尾部会**恢复外键**，于是**第二次以后**重建快照时 `DROP TABLE product` 被 `fk_activity_product` 挡住（`ERROR 3730`）——DROP 顺序无法同时满足所有父子关系。修法：**DROP 与 `SET FOREIGN_KEY_CHECKS=0` 写在同一条 `--execute` 里**（该开关是会话级的，而脚本每次调用都是独立 mysql.exe 进程），实测重建恢复正常。
- **坑（已写进代码注释）**：① `Map<Long,String>` 的 JSON key 只能是字符串 → HTTP 适配器显式把 key 还原成 `Long`（不要直接反序列化 `Map<Long,String>`）；② HTTP 适配器放**单体**（bootstrap）而不是 analytics —— 不给域模块加 web 依赖（Day 5/7 的"依赖传播"教训）；③ 本日根因值得单独记：**同一个适配器类在不同宿主体内语义不同**（`OrderQueryAdapter` 在 order 应用读权威、在单体读副本）—— 这类"随宿主变义的共享实现"必须用开关把角色显式化，否则读错库是无感的。
- **诚实说明**：① `seckill_activity_snapshot` 表仍在旧库、由单体的 admin 手动触发写入（`captureSnapshot`）→ **旧库最后一件遗留物**，本日不动，需在收官日一并决定它的归属（表是空的，且没有任何读路径依赖它）；② rank 分支与回归结果两项待回收（见上）；③ Redis 隔离仍未做 → 检查表剩 **1 项**。

---

### P4 Day 9 详细任务卡（旧库下线检查表·最后 1 项：Redis 隔离）

> 执行方式：panda 实现 + 导师现场纠偏；预估 1~2 小时。

**目标**：测试**不再消费/污染业务 Redis 库**。Day 5 只隔离了 MySQL（测试库 `flash_sale_test`），Redis 仍共享 —— 测试会把自造消息投进**业务 Stream** 再消费，风险有两层：① 业务库里出现测试键/测试消息；② 若 order 应用恰好在跑，它的消费者会**抢走**测试消息（测试挂起/抖动）。

**侦察结论**：

- 业务 Redis 是 **`spring.data.redis` 的 `database=1`**（单体与 order 应用**必须同库**：订单 Stream、消费组/PEL、实时榜 ZSet 全在这里 —— order yaml 有注释明说）。
- 键形状：主 Stream `seckill:order:stream`、死信 `seckill:order:dead:stream`、消费组 `seckill:order:group`（`CacheKeyConstant`）。
- 测试侧现状：`flash-sale-bootstrap` / `flash-sale-auth` / `flash-sale-ai` 有 test properties（覆盖了 nacos、数据源、镜像、order-query），但**都没有覆盖 Redis 库索引** → 集成测试全落在 **db 1**（业务库）✗。
- 真正碰 Redis 的用例集中在 bootstrap：`LuaStockDeductionTest`（库存键）、`Phase1IntegrationTest`（键扫描）、`SeckillMessageReliabilityTest`（投递 + 消费 Stream，Day 5 记录里"回归后 msglog +23"就是它 + 订单核心用例的产物）。
- 集成测试已统一用 `@SpringBootTest(properties = "flash.stream.auto-poll=false")` 关掉自动消费、由测试自己驱动消费者 —— 所以隔离后**不会**出现"测试消息被真实消费者吃掉"的空转。

**设计决定**：**不引入 Testcontainers**（本机无 Docker 依赖，且要跑在 Windows/MySQL 已就绪的环境里），改用 **Redis 逻辑库隔离 `database=2`** —— 一行的成本达到同一目的：测试的 Stream / 消费组 / PEL / 库存键 / 榜单 ZSet 全部落在 db 2，业务 db 1 只增不减地保持不变。

| 步骤 | 内容 |
|---|---|
| Step 1 | `flash-sale-bootstrap/src/test/resources/application.properties` 加 `spring.data.redis.database=2`（+ 说明为什么不能用 Testcontainers） |
| Step 2 | `flash-sale-auth/src/test/resources/application.properties` 同样加一条（它的令牌缓存也用 Redis） |
| Step 3 | **决定性验证**：回归前后读 **db 1** 的 `XLEN seckill:order:stream` / dead / `ZCARD` 榜单 → 必须**逐项不变**；同时 db 2 出现测试 Stream 键（证明测试确实在 db 2 跑）；测试库 msglog 仍增长（证明用例真的跑了消费链路） |
| Step 4 | 回归 + 对账 + 文档 + 提交 |

**验收判据**：① 构建 20 模块绿线；② **db 1 签名回归前后完全一致**（Stream/dead 长度 + 榜单基数 + 抽样键集合）；③ **db 2 有测试活动**（Stream 键出现、PEL/消费组存在）→ 证明隔离是"搬家"而不是"测试没跑"；④ 测试库 `flash_sale_test` 的 msglog 仍增长 → 用例真在消费；⑤ 全量回归 **136 用例 0 失败**；⑥ 服务仍连 db 1（配置未动，只有 test properties 变）；⑦ 对账 18/18；⑧ 文档：Day 9 ✅ + 检查表**清零** → 收官日只剩"删反向镜像 + 旧库下线 + snapshot 归属"。

**坑（预判）**：① `spring.redis.*` 在 Boot 3 已移除，必须写 **`spring.data.redis.database`**；② 隔离后测试首次运行会看到**空的 db 2** —— 若某用例依赖"库里已有历史数据"会变红（本仓用例都是自造数据 ✓）；③ db 2 的残留键会跨轮累积 → 与 Day 5 的教训同理，必要时在用例里自清（或接受累积，因为它只影响测试库）；④ **不要**去清 db 1 里历史遗留的测试键（无法区分测试与业务键，误删=事故）；⑤ order-core 的用例默认连 **db 0**（无配置 → Boot 默认），本来就不在业务库上 ✓，本日不动它，但要在文档里说明这一事实。

**提交计划**：① `test(p4-day9): Redis 逻辑库隔离（测试落 db 2，业务 db 1 零扰动）`；② `docs(p4-day9): plan.md Day 9 完成 + 检查表清零`。

**P4 Day 9 实施记录（2026-10-16）**：

- **改动面**：只有两个 **test properties** —— `flash-sale-bootstrap` 与 `flash-sale-auth` 各加 `spring.data.redis.database=2`（+ 说明为什么不用 Testcontainers：本机无 Docker 依赖，逻辑库隔离是一行成本达到同一目的；并注明 Boot 3 只认 `spring.data.redis.*`）。**生产配置一行未动**（单体与 order 应用仍同库 db 1）。
- **判据（全绿）**：① 构建绿线；② **决定性**：先取 db 1 签名（`stream=0 dead=0 rankZ=0 dbsize=2`）→ 跑一遍含 Stream/库存键的 16 个用例 → db 1 **逐项不变**（`dbsize=2` 不变）✓✓；③ 同一时段 **db 2** 已是测试足迹所在（`dbsize=4`）→ 隔离是"搬家"不是"测试没跑"；④ 全量回归 **136 用例 0 失败**（2.4 分钟）且**测试库 msglog 5198→5221（+23）** → 用例确实在消费链路里跑过；⑤ 对账 **18/18**；⑥ 服务配置未动（只有 test properties 变）。
- **诚实说明（证据口径）**：① 我**没有** Day 9 之前的 db 1 基线快照，所以判定口径是"**测试跑动前后 db 1 完全一致**"（这正是本项要求：测试不再扰动业务库），而不是"db 1 的绝对内容 = 某历史值"；② db 1 现有 2 个键是历史遗留（无法区分测试/业务键，**故意不清**——误删=事故）；③ order-core 的用例默认连 **db 0**（无配置 → Boot 默认），本来就不在业务库上，本日不动它；④ `Redis 隔离` 只覆盖"测试不碰业务库"，**不覆盖**"多个应用共享同一 db 1"这一事实（那是过渡期设计，收官日随旧库下线一起结束）。
- **排障留档**：本日两次把自定义函数命名为 `R` / `Rd`，都被 PowerShell 内置别名吞掉（`R`=`Invoke-History`、`rd`=`Remove-Item`）→ 手写 RESP 探针的函数名要用**动词-名词**（`RedisQuery`）。另：`-Dtest=A,B,C` 在 PowerShell 里必须整串加引号（逗号会被解析成数组）。

---

### P4 Day 10 详细任务卡（收官日：删反向镜像 + 旧库 `flash_sale` 下线）

> ⚠️ **本卡结论：收官不是一天，也不是"删镜像 + DROP"两步**。零假设清点后，旧库只剩**一个读者**（单体）与**两类遗留**，但其中"一处活跃写入"必须先搬家，且搬家会牵动测试口径。因此本日**只出清点 + 定序**，代码改动留到下一步执行。

**零假设清点（实测）**：

| 应用 | datasource schema | 结论 |
|---|---|---|
| product / activity / order / auth | 各自权威库 | ✅ 不碰旧库 |
| seckill | `flash_sale_activity` | ✅ 不碰旧库 |
| ai / gateway | 无数据源 | ✅ 不碰旧库 |
| **bootstrap（单体）** | **`flash_sale`** ✗ | **唯一读者** |

**单体为什么还连着旧库（两类遗留）**：

1. **一条"死链"**（可安全拆除 ✓）：单体的控制器清单只有 `HealthController` + `/internal/seckill`（Redis）——**没有任何商品/活动/订单端点**（那些早就搬到各自应用 ✓）。但它仍在 `@MapperScan` 里扫 `product/activity/order/analytics` 四个包，于是 activity-core 的 `SeckillActivityServiceImpl`、`SeckillCacheService` 与单体自己的 `LocalProductCatalogAdapter` 被实例化 —— 而它们的**唯一调用者是 activity / seckill 两个应用的控制器**，在单体里**永远不会被调用** ✓。
2. **一处活跃写入**（必须先搬家 ✗）：admin 指标快照 `captureSnapshot` → `seckill_activity_snapshot`（**表在旧库、且是空表**）。`MetricsServiceImpl` 的 `snapshotMapper` 只有 `insert`、没有任何读路径 ✓。

**决定性约束（本日侦察挖到的坑，直接决定收官顺序）**：单体**不能简单删掉 `order.mapper` / order 侧 Bean** —— bootstrap 的**测试属性**里写着 `module.analytics.order-query=local`（Day 8 为了单进程测试加的），它需要 order-core 的本地适配器 Bean ✓；而**生产**单体是 `remote`。若把 `order` 从单体扫描面里去掉，测试会红在"端口无实现"。⇒ 收官必须**同时**处理"扫描面 / 端口实现 / 测试口径"三件事，不能只删 mapper 扫描。

**收官三步（定序，每步都可独立验证 + 可回退）**：

| 步骤 | 内容 | 验收 |
|---|---|---|
| **Step A 拆死链** | 单体收窄 `scanBasePackages`（只留自己 + analytics + user(auth) + infra + remote + common/contracts），`@MapperScan` 只留 `analytics.mapper`；**同时**给 bootstrap 测试补一个 `ProductCatalogPort`/`OrderQueryPort` 的测试替身（或测试侧继续 `local` 但把 order-core 保留在测试 classpath 上） | 启动无障碍；回归绿；`flash_sale` 上不再有 product/activity 的表访问 |
| **Step B 快照搬家** | `seckill_activity_snapshot` 迁到 `flash_sale_order`（order 应用已扫 `analytics.mapper`，且消费链路就在它那儿）+ admin 的 `captureSnapshot` 端点**由 order 应用承载**；单体侧移除 analytics 的 snapshot 依赖 | 迁移后 `captureSnapshot` 在 order 应用可用；单体 `@MapperScan` 可**全空**（或仅留必要） |
| **Step C 下线** | 单体 datasource 换成…… 见下（**单体将不再需要数据源** → 直接 `exclude DataSourceAutoConfiguration`，与 ai 同法）；随后：停镜像 → 观察一轮回归 → 删镜像代码（`DualWriteMirror`/`OrderMirror`/`AuthMirror`/`DualWriteProperties` + 18 个写点）→ 逻辑备份 → `DROP DATABASE flash_sale` | 单体 health 无 `db` 组件；对账脚本退役（改为权威四库自检）；回归绿 |

**Step C 的连锁收益（值得写进迁移叙事）**：单体一旦没有数据源，它就从"带库的单体"变成**纯编排/边缘应用**（路由 + 会话守卫 + 内部端点 + ai 代理），旧库的最后一根线随之断开 —— 这是三阶段迁移的终点，也是"单体退化为 BFF"的自然结论。

**风险与回退**：① Step A 的测试替身是唯一"新代码"，可用 `@MockBean` 或 `@TestConfiguration` 提供，避免为测试保留生产依赖；② Step B 的端点搬家要同步前端调用路径（当前 `/api/admin/seckill/activities/{id}/metrics` 与 capture 同族）；③ Step C 前**必须**有一份逻辑备份（`mysqldump flash_sale`）+ 一份"旧库只读观察期"的门禁数据。

**本日交付**：仅本卡（清点表 + 三步定序 + 三处约束）。**不改代码**——因为 Step A 一旦动手就必须同步改测试口径，属于"一步到位"的改动，不能半程收工。

**P4 Day 10 执行记录（2026-10-16）**：

**⚠️ 定序修正：B → A → C（原卡写的是 A → B → C）**。执行前侦察发现决定性证据：**单体测试套件与全域 Bean 深度耦合** —— `CreateBusinessValidationTest` 直接 `@Autowired`/`import` 了 `ProductServiceImpl`、`SeckillActivityServiceImpl`、`ProductMapper`、`SeckillActivityMapper`、`ProductCacheService`、`SeckillCacheService`、`ProductCatalogPort`；`AdminSeckillResetStockTest` 也注入 `SeckillActivityMapper` + `SeckillCacheService`；另有约十个集成测试各自 autowire 十来个域 Bean。⇒ **Step A 的真实成本是"测试套件搬迁"（把域级集成测试搬到各自应用，或改为端口替身），不是删几行 `@MapperScan`** ✗。若先做 A，主干会在"单体缺 Bean"的中间态上停留很久（违背绿灯纪律）。而 **Step B 是纯加法**（建表 + 端点搬家），不碰现有装配 ✓ ⇒ **先 B 后 A**。

**Step B 数据面第一半（已完成 ✓，可回退）**：

```
源表实测：seckill_activity_snapshot（旧库，0 行）
  id / activity_id / redis_stock / order_count / queued_message_count / success_count /
  duplicate_reject_count / rate_limit_reject_count / sold_out_reject_count / snapshot_time
  PRIMARY(id) + KEY idx_snapshot_activity_time(activity_id, snapshot_time)
  CONSTRAINT fk_snapshot_activity → seckill_activity(id)
执行：CREATE TABLE IF NOT EXISTS flash_sale_order.seckill_activity_snapshot
        LIKE flash_sale.seckill_activity_snapshot;      -- 幂等，纯加法
校验：列数 10/10 ✓ · 索引数 2/2 ✓ · 新表 0 行 ✓ · 旧库表仍在且仍为 0 行 ✓（未改动）
```
**一处结构性发现（值得记）**：`LIKE` 不复制外键，而本表在 `flash_sale_order` 里**根本无法**重建那条 FK —— 它的引用目标 `seckill_activity` 的权威在 `flash_sale_activity`（另一个库），跨 domain 的引用完整性在这里是**跨服务**问题，不是 DDL 能表达的。因此新表无 FK 是**结构性必然**（而非取舍），这一点与 Day 5"丢外键致失败路径静默成功"的教训并不冲突：那张表是 write-only、且当前 0 行。

**Step B 剩余第二半（未做）**：让 **order 应用**承载 `captureSnapshot` 端点（`AdminMetricsController` 现在被单体与 order 同时扫到 → 需要按宿主加开关，形如 Day 7/8 的 `module.*` 能力开关），并在单体侧移除对 `analytics.mapper` 的依赖。做到那一步，单体的 `@MapperScan` 才能清空。

**Step B 第二半（已完成 ✓ 2026-10-16）—— 端点归属搬家 + 网关补精确路由**：

| 文件 | 动作 |
|---|---|
| `flash-sale-order/.../order/controller/AdminMetricsCaptureController.java` | **新增**：`POST /api/admin/seckill/activities/{id}/snapshot` → 委托 `MetricsService.captureSnapshot`（权威库 `flash_sale_order` 在此，表 Day 10 已建 ✓） |
| `flash-sale-analytics/.../AdminMetricsController.java` | **删除写端点**，只留读端点 `GET /{id}/metrics`（类被单体与 order 同时扫到都无害） |
| `flash-sale-gateway/.../application.yaml` | order 路由补**精确** Path `/api/admin/seckill/activities/*/snapshot`（不加会落到 monolith 的 `/api/**` 兜底上 —— 先匹配先赢） |

- **判据**：构建 ✅；定向用例 `SeckillMetricsTest` **6/0/0** ✅（该用例直接调服务、不走端点，所以搬家不打穿它）；**全量回归 136/0/0/0** ✅；业务库 + 四权威 schema 逐表不变（含新表 `ord…/0`）✅；对账 **18/18** ✅；结构证据：写端点**只**在 order 应用（`AdminMetricsCaptureController`）、analytics 控制器只剩读 ✅。
- **尚未完成的一步（诚实记录）**：单体 `@MapperScan` 里的 `analytics.mapper` **还不能删** —— `MetricsServiceImpl` 的构造器直接注入 `SeckillActivitySnapshotMapper`（写快照用），即使单体侧不再有写端点（端点是入口，Bean 是装配）。要真正清空单体扫描面，需把该依赖改为 `ObjectProvider<SeckillActivitySnapshotMapper>`（缺 mapper 时构造成功、调用才报错）或把写方法拆出去 —— 这与 Step A 一起做。

**Step B 收尾（已完成 ✓ 2026-10-16）**：`MetricsServiceImpl` 的快照写入由构造器直连改为
`ObjectProvider<SeckillActivitySnapshotMapper>`（缺 mapper 的宿主照样装配成功，真调用才报可诊断错误）—— 判据：构建 ✅、定向 `SeckillMetricsTest` 6/0/0 ✅、**全量回归 136/0/0/0** ✅、工作区 clean（`7e543df`）。**→ Step B 三件套（数据面 / 端点归属 / 装配解耦）全部完成。**

**Step A 执行设计（已定稿，待执行 —— 关键是把"测试要什么"与"生产要什么"分开表达）**：

| 面 | 动作 |
|---|---|
| **生产（单体）** | ① `@SpringBootApplication(scanBasePackages = {自己.controller, 自己.analytics, user, infra, remote, common, contracts})` —— **不列** 根 `adapter`（那里面是已死的 `LocalProductCatalogAdapter`）、也不列 product/activity/order；② `@MapperScan` **整段删除**（生产已无 mapper 需求：快照走 `ObjectProvider` ✓、订单走 HTTP ✓、会话走 HTTP ✓）；③ yaml 加 `module.activity.cache-service: false`（死链里最后一个还要求 mapper 的 Bean） |
| **测试（单体）** | 新增 **一个** 测试配置类，放在**被扫描的包**里（如 `com.ghb.ecommerceflashsalesystem.analytics`）→ `@SpringBootTest` 会自动拾取 ✓：`@Configuration + @ComponentScan({"….product","….activity","….order"}) + @MapperScan({"….product.mapper","….activity.mapper","….order.mapper","….analytics.mapper"}) + @Import(LocalProductCatalogAdapter.class)`。**理由**：那 ~10 个集成测试是**有意**把单体当"全域装配沙盘"跑域 Bean 的（`CreateBusinessValidationTest` 注入 `ProductServiceImpl`/`SeckillActivityServiceImpl`、`AdminSeckillResetStockTest` 注入 `SeckillCacheService`、`SeckillMessageReliabilityTest` 注入 order-core 的消费者）→ 这些需求属**测试**，不该由**生产**扫描面承担；放进测试树后生产收窄、测试照旧 ✓ |
| **守卫（新）** | 在 `DomainEndpointsMovedTest` 加两条断言：① 单体的 `@MapperScan` 不存在或为空；② 生产上下文里**没有** `SeckillCacheService`/`SeckillActivityServiceImpl` Bean（与"死链"结论对齐，防回潮） |
| **验收** | 构建 ✅；**全量回归 136/0/0/0**（测试**零改动**，靠测试配置补齐 Bean）；对账 18/18；结构证据 = 扫描面收窄 + 守卫断言绿 |

**为什么这版设计能成立**：Day-10 侦察发现"A 的成本在测试耦合"，于是把耦合**搬到测试侧显式表达**（一个配置文件），生产侧就能真的瘦下来 —— 这是"装配面"与"测试沙盘"职责分离，而不是为测试保留生产依赖。

**Step A 已完成（✓ 2026-10-16，`c515c06`）**：单体 `scanBasePackages` 收窄为 `{.controller, .analytics, .user, .infra, .remote, .common, .contracts}` + **`@MapperScan` 整段删除** + `@Import(SeckillStockService.class)`；测试侧新增 `analytics/TestDomainContextConfig`（`@ComponentScan(product/activity/order/seckill)` + `@MapperScan(四包)` + `@Import(LocalProductCatalogAdapter.class)`，放在被扫描的包里以便 `@SpringBootTest` 自动拾取）。判据：构建 ✅、沙盘代表用例 **26/0/0** ✅、**全量回归 136/0/0/0（测试文件零改动）** ✅。执行中躲过的坑：`SeckillStockService`（单体 `/internal/seckill` 仍需要它，字段只有 RedisTemplate）与 `SeckillServiceImpl`（要 activity.mapper）同在 `seckill.*` 之下 → 必须用 `@Import` **精确引入单个 Bean**，扫包会把 `service.impl` 一起带回来。

**Step C 启动验证（⚠️ 阻塞已定位，2026-10-16）**：实测 `exclude = DataSourceAutoConfiguration.class` 后单体**启动失败** ——

```
Parameter 0 of constructor in com.ghb.ecommerceflashsalesystem.controller.health.HealthController
required a bean of type 'javax.sql.DataSource' that could not be found.
```
⇒ 去数据源必须与"健康检查改为可选依赖"**一起做**：把 `HealthController` 的 `DataSource` 改成 `ObjectProvider<DataSource>`（无库时该组件报 `db: absent/unknown`，有库时照旧），然后才能 `exclude` 并删 yaml 的 datasource 段。**已回退**（保持可启动），并在启动类留了注释指针。**Step C 剩余顺序**：① 健康检查可选化（1 处）→ ② `exclude` + 删 datasource 段 + 单跑单体验证 health 无 `db` → ③ 停镜像（三个应用的 `module.db.dual-write.enabled=false`）观察一轮回归 → ④ 删镜像代码（`DualWriteMirror`/`OrderMirror`/`AuthMirror`/`DualWriteProperties` + 18 个写点）→ ⑤ `mysqldump flash_sale` 备份 → ⑥ `DROP DATABASE flash_sale` + 对账脚本退役（改权威四库自检）→ ⑦ 守卫断言（单体无 `@MapperScan`、生产上下文无死链 Bean）。

**Step A 的准确范围（改由下一单元承载）**：① 单体收窄 `scanBasePackages` + `@MapperScan` 只留必要；② **测试套件搬迁/替身化**（上面点名的两个测试 + 约十个 autowire 域 Bean 的用例）—— 这一项必须作为一个完整单元推进，且要在 Step B 完成后进行（那时快照已不在单体侧）。

---

## 🎯 P4 收官记录（2026-10-16 ~ 10-17）—— 三阶段迁移终点

**旧库 `flash_sale` 已 `DROP DATABASE` 下线。现存 schema：`flash_sale_product` / `flash_sale_activity` / `flash_sale_order` / `flash_sale_auth` + 测试库 `flash_sale_test`。**

### 收官执行顺序（实测走完，含两次红）

| 步 | 内容 | 判据/证据 |
|---|---|---|
| **A 单体瘦身** | `scanBasePackages` 收窄 + **`@MapperScan` 整段删除** + `@Import(SeckillStockService.class)`（精确引入，避开同域下要 `activity.mapper` 的 `SeckillServiceImpl`）+ 测试侧 `analytics/TestDomainContextConfig`（`@ComponentScan` 域包 + `@MapperScan` 四包 + `@Import(LocalProductCatalogAdapter)`，放在**被扫描的包**里让 `@SpringBootTest` 自动拾取） | 回归 **136/0/0**（测试**零改动**）；生产单体能真起（rank code=0） |
| **B 快照搬家** | ① `flash_sale_order` 建同构 `seckill_activity_snapshot`（10 列/2 索引/0 行）；② 写端点按归属搬到 order 应用（`AdminMetricsCaptureController`，网关补精确 Path），analytics 控制器只留读；③ `MetricsServiceImpl` 的快照依赖改 `ObjectProvider`（无 mapper 的宿主也能装配） | build ✓ / 定向用例 ✓ / 回归 136/0/0 ✓ / 签名逐表不变 ✓ |
| **C-①② 单体去数据源** | 健康检查改 `ObjectProvider<DataSource>`（无库报 `mysql=ABSENT`）+ **属性化**排除 `DataSourceAutoConfiguration`（`spring.autoconfigure.exclude`，测试 properties 置空即恢复） | 单跑单体：`8081` UP、`actuator.health` **无 `db` 组件**、`/api/health` = `{"application":"UP","mysql":"ABSENT","redis":"UP"}`；回归 **136/0/0** |
| **C-③ 停镜像** | activity/auth/order/product 四处 `dual-write.enabled` 默认 `true → false` | 回归 136/0/0 |
| **C-⑤ 备份** | `_legacy-backup/flash_sale_20261016.sql`（1.6 MB，`CREATE DATABASE` + 7 表 + 数据），目录进 `.gitignore` | 文件大小/内容校验 ✓ |
| **C-⑥ 下线** | ① 测试库脚本**自足化**：源=四权威库逐表映射（7 表行数全 ✓）、**外键改为脚本内显式声明 5 条**（原从源库 `information_schema` 反查 —— 权威库零外键 + 旧库消失后无源可查）；② 对账脚本**退役**（12 处旧库查询，文件头写明原因、保留 18 项定义作迁移史档案）；③ 守卫：备份 >1MB 才 `DROP DATABASE flash_sale` | 快照脚本退出码 0、`test.fk=5`、FK 相关用例 **3/0/0**；**DROP 已执行**；终态 schema 清单确认旧库消失 |
| **C-⑦ 守卫断言** | 新增 `guard/PostMigrationInvariantsTest`（纯 JUnit，4 条）：单体无 `@MapperScan`、装配面不含 domain 包、数据源排除必须走**属性**（类级 `exclude` 不许用 —— 它测试撤销不了，正是首轮红掉的原因）、`DualWriteProperties.enabled` 类内默认必须 `false` | **4/0/0（0.1 秒）** |

### 两个必须记住的教训

1. **"可被测试覆盖"是配置机制的一等公民**：同样是"排除数据源"，类级 `@SpringBootApplication(exclude=…)` 编译进 classpath、测试撤销不了 → 集成测试全红；属性 `spring.autoconfigure.exclude` 可被测试 properties 置空覆盖 → 生产无库、测试有库，两者并存。**选机制时先问"谁能覆盖它"。**
2. **下线一个数据库 = 先找出所有"以它为唯一来源"的工具链**：测试库快照脚本（源库）、外键恢复（从源库反查）、对账脚本（12 处对比查询）—— 这三处都不是业务代码，`grep` 业务代码找不到它们。**DROP 前必须清点 scripts/ 与运维侧，而不只是 src/。**

### 遗留项已清零（2026-10-17 完成）

**反向镜像死代码已全部删除**（`31129a0`）：

| 面 | 内容 |
|---|---|
| 删除的类 | `infra.db.DualWriteMirror`、`infra.db.DualWriteProperties`、`order.mirror.OrderMirror`、`user.mirror.AuthMirror` |
| 写点清理 | 7 个服务类 / 约 18 处：`SeckillCacheService`(3)、`SeckillActivityServiceImpl`(3)、`ProductServiceImpl`(3+helper)、`SeckillOrderConsumer`(5)、`DeadLetterReplayService`(1)、`UserAccountService`(6)、`UserSessionService`(5) —— 均连同 import / 字段 / 私有 helper 一并删除 |
| 测试同步 | 手工构造点 6 处（`new SeckillCacheService`×2、`new ProductServiceImpl`、`new SeckillActivityServiceImpl`、`new UserAccountService`、`new UserSessionService`）+ mock/断言清理；`UserSessionServiceTest` 的镜像断言改为"不再有交互" |
| 守卫升级 | `PostMigrationInvariantsTest` 第 4 条由"`DualWriteProperties.enabled` 默认 false"升级为**"四个镜像类必须已不存在"**（`Class.forName` 断言）—— 比"默认关"更强的不变量 |

**分 6 个提交完成，每步都过门禁**（`1482cc1` → `5998277` → `78546eb` → `fb793b2` → `2a60230` → `31129a0`），基线始终 **140/0/0**。

**过程中撞到并记录的两条规律**（下次删共享组件可直接复用）：
1. **删字段 = 改构造器**：Lombok `@RequiredArgsConstructor` 的签名随字段变，凡是**手工 `new` 过该服务**的测试都会编译失败 → 必须"服务类 + 其测试"成对改（Day 10 首次尝试就是因此红掉并回退的）。
2. **删类 = 清导入**：`import` 一个已删除的类会直接编译失败（不只是警告）→ 删类前先做全仓引用清点（本次 6 个测试文件、9 条 import）。

### 收官后优化①：会话校验的短 TTL 本机缓存（2026-10-17，`64d95fe` + `6589a5e`）

| 面 | 内容 |
|---|---|
| 位置 | `flash-sale-auth-core/.../user/web/SessionGuard.java`（三个运行时的**唯一**会话入口） |
| 开关 | `module.auth.session-cache-ttl`，**默认 `0s` = 关闭**（行为与引入前完全一致） |
| 策略 | 只缓存**有效**结果；`ConcurrentHashMap` 上限 1 万条、超限整体清空；TTL 到期即失效 |
| 收益 | 同一页面连续请求（列表 + 详情 + 轮询）不再每个都打一次 auth 内部校验接口 |
| 代价（写进类注释） | 吊销（登出/改密/禁用）**生效延迟 ≤ TTL**，且各运行时各自缓存、互不同步 ⇒ 启用请配短值 |
| 测试 | `SessionGuardTest` **3/0/0**：关闭=逐次校验、TTL 内只校验一次、过期重新校验（用**手写计数端口**断言"下游被调用几次"，不用 Mockito 的 verify 家族） |
| 回归基线 | **143/0/0**（140 + 3 条缓存语义） |

**一条排障留档**：`flash-sale-auth-core` 的测试 classpath 里 Mockito 的 `verify/times/anyString` 静态导入不可用（`mock`/`when` 可用），且该模块**没有 `spring-test`**（`ReflectionTestUtils` 不可用）→ 新测试请用"手写替身 + 原生反射"，并把编译错误**落盘后再读**（控制台是 GBK，`符号: …` 那行会被乱码吞掉）。

---

## 十、第 5 阶段 · P5：读模型收口（analytics 事件驱动，CQRS 完成）—— 规划卡（2026-10-17）

> **为什么是它**：P4 收官后**写侧**已全部就位（五个服务各持权威库、单体无库、镜像与旧库都已清除）；还剩**读侧**一处"查询时跨服务取数"——`MetricsServiceImpl` 每次请求都要调 order 服务的 `/internal/orders/activities/{id}/count` 与 `/order-nos`（Day 8 的过渡实现）。P5 把它换成**事件驱动投影**，真正的 CQRS 读侧，目标里程碑 **M6 读模型分离**。

**当前读侧取数现状（待 Day 1 零假设复核）**：

| 端点 | 当前来源 | P5 后来源 |
|---|---|---|
| `GET /api/admin/seckill/activities/{id}/metrics` | Redis 计数（Lua 写入）+ **HTTP 调 order 取订单数** | Redis 投影（订单数由事件累加） |
| `GET /api/rank/top10?activityId=` | Redis ZSet `seckill:rank:<id>`（消费成功时 `ZADD`）✅ 已是投影 | 不变（补回填/对账） |
| 快照 `POST .../snapshot` | order 应用落库 `seckill_activity_snapshot` | 不变（写侧已归属 order） |

**Day 1 — 现状盘点 + 事件契约（零假设，不写业务代码）**
- 盘点：`MetricsServiceImpl` / `SeckillRankService` / `AdminMetricsController` 的每条取数路径（Redis / 跨服务 / 读库）逐条列清；
- 盘点写侧事件：`seckill:order:stream` 消息字段、`seckill_message_log` 状态机、榜单 `ZADD` 的写入点；
- 产出：**事件契约冻结**（复用既有 Stream 消息还是新增 `order.created`；字段落 `contracts`）+ 投影键设计（`seckill:metrics:<activityId>` Hash）+ 幂等键（messageId / orderNo）；
- 判据：一份"读侧取数矩阵"（端点 → 当前来源 → P5 后来源）入档。

**Day 2 — 投影器 + 回填（可回退：旧路径保留，开关切换）**
- 投影写入点：消费者落单成功后累加（`HINCRBY` 订单数/成功数），与既有 `ZADD` 同处，保证**同一事务语义内**；
- **幂等**：以 messageId 去重（Redis `SETNX` 或投影内 `lastMessageId`），重复投递不重复计数；
- **回填**：从权威库 `flash_sale_order.seckill_order` 重建某活动投影（脚本或内部端点），用于上线与漂移修复；
- 判据：用例覆盖"重复消息不重复计数""回填后与权威库一致"。

**Day 3 — 切读 + 对账 + 收口**
- `MetricsServiceImpl` 改读投影：开关 `module.analytics.metrics-source=projection|order`（默认 projection，回退即切回 order）；
- 对账脚本：投影 vs 权威库逐活动比对（输出差异表；沿 `reconcile-*.ps1` 的既有风格）；
- 全量回归（基线 **143**）+ 经网关实测（orderCount 与库一致）+ 文档 + `git tag p5-done`；
- 判据：读端点**不再有**到 order 的同步调用（结构证据）+ 对账 **0 差异** + 回归绿。

**风险与回退**

| 风险 | 处置 |
|---|---|
| 投影漂移（丢消息 / 重复投递） | 幂等键 + 回填工具 + 对账脚本（发布前/每日跑） |
| 切读口径与旧实现不一致 | **双读观察期**：投影与 order 计数同时取、差异打日志观察一轮，再切默认值 |
| 事件契约变更牵连 seckill/order | 契约放 `contracts`（与既有事件 record 同处）、字段版本化 |
| 回退 | 开关切回 `order` 即恢复 Day 8 行为（投影侧只增不删） |

**M6 完成判据**：① 指标与榜单全部读投影、查询路径零跨服务调用；② 投影可从权威库重建；③ 对账 0 差异；④ 回归绿（143 基线，新增用例后更高）；⑤ 文档 + 回退开关齐备。

---

*文档创建日期：2026-07-29*
*上次更新：2026-10-14（**P1-2 收尾完成：T1~T5 + 全量绿线**：Nacos 2.5.4 本机 standalone（单体注册 `flash-sale-monolith`，healthy）；网关 `lb://` 服务发现路由；**T5 观测栈**（WSL `redis` 发行版 Docker，Prometheus v2.53.0 + Grafana 11.1.0，targets 3/3 UP、数据源与看板 provisioning、6 面板出图，提交 `289e7e3`）；**停应用后全量回归 `mvnw clean test` → 113 用例 0 失败 BUILD SUCCESS（1.1 分钟，14 模块全绿）**；环境级结论：观测栈须 `network_mode: host`、Windows 侧访问须 `127.0.0.1`、观测栈归属 WSL `redis` 发行版且重启后需唤醒；`deploy/README.md` 含完整观测栈说明与排查。另修复阻塞性环境问题：Maven 镜像 `http → https`；**P2 Day 1 完成**（`flash-sale-auth` 可启动骨架：`:8082` health UP、Nacos 服务列表 3 个、零行为变更）；**P2 Day 2 完成**（`flash-sale-auth-core` 库抽取 + 单体摘除 auth 域 + 网关精确路由；经网关 auth 链路 `code=0`、带有效凭证直连单体 404、其余业务仍自持鉴权；install exit=0 / core 单测 13/13 / ArchitectureTest 16/16）；**P2 Day 3~4 完成并收口**（ai 服务化 :8083 + 网关准入鉴权 + 全量 **124 用例**绿线 + `p2-done` 标签；提交 `1014a8e`/`7db07eb`/`4275ba2`）；**P3 Day 1 完成**（product 域拆为 `flash-sale-product-core` 库 + `flash-sale-product` 应用 :8084；零行为变更 —— 网关路由未改、停 8084 后经网关商品接口仍 200；Nacos 5 服务；全量 124 用例绿线；改名提交 `3afc88b`）；**P3 Day 2 完成**（product 接口迁入应用 + 网关导流含 `/uploads/**`；单体 404 / 新服务 200 / **停 8084 经网关 503**；上传→回读 200；全量 **125 用例**绿线）；**P3 Day 3 完成**（product / activity 独立 schema + 幂等迁移 + 双写镜像（默认关）+ 对账脚本；新库外键归零、列/索引逐项一致；双写开 8/8 一致、关时新库不动；脚本正/负向双向验证；全量 125 用例绿线）；**P3 Day 4 完成**（activity 库改名 `flash-sale-activity-core` + 新应用 `:8085` + 控制器一分为二（路径不变）+ 网关精确三条 Path；停 :8085 → 三条 activity 路径 **503** 而 metrics 仍 200；双写 22/22 + 对账 8/8；全量 **126 用例**绿线）；**P3 Day 5 完成（P3 收口）**（库存权属收口：seckill 内部接口 + 端口化委托，activity 不再写 `seckill:stock:*`；`/internal/**` 与 `/api/admin/**` 双护栏 fail-closed（直连两应用无令牌 → 40100）；去跨域直读：activity 不再依赖 product-core，商品校验改调商品服务接口（停 :8084 → `50000` 且零写入）；双写 22/22 + 对账 8/8；全量 **129 用例**绿线）；**P4 Day 1 完成（切读：翻转权威源）**（两应用数据源 → 新 schema，镜像语义反转为"写副本旧库"且应用侧默认开启；门禁 + 切换后对账均 8/8；镜像日志 `target=*(db=flash_sale)`；未拆模块经旧库读到新数据；决定性判定用"新 id 探针"证明读权威、不读副本；全量 **129 用例**绿线）；**P4 Day 2 完成（seckill 域服务化）**（改名 seckill-core + 共享 HTTP 适配器模块 flash-sale-remote + 新应用 `flash-sale-seckill` :8086（带 auth-core、数据源指旧库）+ 网关四条精确 Path；**execute 端到端**：`code=0 QUEUED` + 重复 `40901 DUPLICATED` + 无令牌 `40100` + 订单经 Stream 在单体落库（`seckill_order`/`seckill_message_log` 命中）；停 :8086 → activities 503 而 order 域路径仍 200；直连单体公开读 404；对账 8/8；全量 **130 用例**绿线）；**P4 Day 3 完成（order 域服务化）**（改名 order-core + 新应用 `flash-sale-order` :8087（带 auth-core + analytics）+ **只搬调度入口**（消费者本体留库，4 个集成测试注入）+ 3 个入口随域迁入 + 网关四条精确 Path；**消费迁移正反双向**：`:8087 [scheduling-1] 订单落库成功并ACK` + `seckill_order` 落库 + 榜单更新，而单体 `订单落库成功` **0 次**；停 :8087 → order 路径 503 而 seckill 域仍 200；直连单体死信端 404；对账 8/8；全量 **131 用例**绿线）；**P4 Day 4 完成（order 的「库」拆分）**（新 schema `flash_sale_order`：`LIKE` 建表零外键 + `REPLACE` 幂等迁移 4027/5174 行 + `OrderMirror` 六写点反向镜像（日志 `target=order(db=flash_sale)`）+ 对账扩到 **14 项**；**双数据源过渡桥**解 auth 域未拆（`user_session` 只在旧库）；**本日最大的坑**：MyBatis-Plus 的 `sqlSessionFactory` 带 `@ConditionalOnMissingBean` → 声明 auth 工厂后主扫描落到它上面，**订单查询静默读副本**，修法 = 主工厂显式声明（约定名）+ 显式 `sqlSessionFactoryRef` + 启动自检日志；判定用"只改权威 / 只改副本"双向改值；全量 **131 用例**绿线）；**收尾发现"影子消费"**（回归测试跑在单体 JVM、消费者扫真实 Stream → 旧库多 23 行，查明为重复消息无订单 → 清理回 5175/5175，并留档"回归后先清影子行再对账"的约定 + Day 5 做测试数据源隔离）；**P4 Day 5 完成（ai 摘出旧库 + 测试数据源隔离）**（ai 新增 `CatalogQueryPort` 走服务**公开接口**、摘掉数据源/mapper/mybatis → **健康不再有 db 组件**；反证=停商品服务即目录降级；收窄扫描面修 `SeckillCacheService` 缺失 mapper 的启动失败；测试隔离 `flash_sale_test` 快照（DROP+CREATE+**重建外键**）× bootstrap/auth；**回归 132 用例 0 失败 + 业务库与三个权威 schema 逐表行数不变** + 对账 14/14；旧库下线检查表写入第九章末）；**P4 Day 6 完成（auth 域库拆分 + 清理任务归属收口）**（新 schema `flash_sale_auth`：`LIKE` 建表 + **补回 `fk_session_user` 外键** + 幂等迁移 115/115、133/133；`AuthMirror` 六写点反向镜像（日志 `target=auth(db=flash_sale)` ×5）；**顺带修潜伏 bug** —— `@Scheduled` 过期会话清理原本被 monolith/order/seckill **三个运行时**同时触发，调度入口按"文件位置表达归属"移到 auth 应用（`AuthSessionCleanupScheduler`）+ 两侧清理；测试侧关镜像避免污染业务库；对账 14 → **18 项**、全量 **136 用例**绿线、旧库签名逐字段不变；检查表剩 3 项）；**P4 Day 7 完成（会话校验跨服务化 → 拆掉 order 双数据源桥）**（`contracts` 定端口 `SessionValidationPort`（**不能放 auth-core**，否则 remote 反依赖会把 Web 装配带进 ai/activity）+ auth-core 的 `LocalSessionValidationAdapter`/`SessionGuard` + remote 的 `HttpSessionValidationAdapter`（fail-closed）+ auth 应用 `POST /internal/auth/sessions/validate`；拦截器改道、两个写服务开关化；**删 order 双数据源桥 + 三处 `user.mapper` 扫描 + order `jdbc-url`→`url`**；seckill 数据源切权威库；**反证** = 停 auth 后带会话请求报「会话校验服务不可用：I/O error on POST .../sessions/validate」（文字级证明跨进程）；回归 136 用例绿线 + 业务库与四个权威 schema 逐表不变 + 对账 18/18；检查表剩 2 项）；**P4 Day 8 完成（analytics 订单读路径跨服务化，含两项待回收）**（`OrderQueryPort` 新增 HTTP 实现（单体 `module.analytics.order-query=remote`，LB + `X-Internal-Token` + fail-closed）+ order 应用新增 `/internal/orders/**` 两个端点；本地实现加 `local` 开关 → order 应用查权威库、单体调服务；**反证** = 停 order 应用后 metrics 报「订单查询服务不可用：I/O error on GET http://flash-sale-order/internal/orders/.../count」；**收尾已回收**：回归 **136 用例 0 失败**（首次红 10 个 = bootstrap 测试无 order 服务 → 测试上下文改 `local`）、业务+四权威 schema 逐表不变、对账 **18/18**；rank 分支用**直打内部端点**判别（`count`→1、`order-nos`→真实订单号、无令牌→40100 ⇒ 服务端与契约钉死；客户端 POST 分支仍未覆盖，如实留档）；另修 `create-test-db.ps1` 的 DROP 撞外键（Day 5 恢复外键引入的潜伏 bug）；旧库最后遗留物 = 单体的 `seckill_activity_snapshot` 写入路径）；**P4 Day 9 完成（Redis 隔离 → 检查表清零）**（只改两个 test properties：`spring.data.redis.database=2`；**决定性判据** = 跑 16 个含 Stream/库存键的用例前后 **业务 db 1 逐项不变**（`stream/dead/rank=0、dbsize=2`），而 db 2 承载测试足迹、全量回归 136 用例 0 失败且测试库 msglog +23（用例真跑）；生产配置一行未动；**下一步只剩收官日：删反向镜像 + 旧库 `flash_sale` 下线 + snapshot 归属**））*
*下次开始位置（**P4 全部完成：旧库已 DROP + 镜像死代码已删 + 基线 140/0/0**）：（历史）**唯一的卫生项 = 删反向镜像死代码** —— `DualWriteMirror` / `DualWriteProperties` / `OrderMirror` / `AuthMirror` 四个类 + 9 个服务类里约 18 处写点调用（`SeckillCacheService` 3 / `SeckillActivityServiceImpl` 3 / `UserAccountService` 8 / `UserSessionService` 6 / `SeckillOrderConsumer` 7 / `DeadLetterReplayService` 3 / `ProductServiceImpl` 3 / 两个启动类的 `@EnableConfigurationProperties`）；顺序：**先删调用点、再删类**（反之编译不过），每步一轮全量回归，最后一次提交；顺带把 `DualWriteProperties` 里仍指向已消失旧库的 `legacy*Url` 默认值一并清掉。（历史）**Step B 收尾那一小步** —— 把 `MetricsServiceImpl` 的 `SeckillActivitySnapshotMapper` 改成 `ObjectProvider`（或拆出写方法），使单体 `@MapperScan` 真的能清空；**Step A** —— 单体收窄 `scanBasePackages`/`@MapperScan` **并同步搬迁测试套件**（`CreateBusinessValidationTest` / `AdminSeckillResetStockTest` 等 ~10 个用例直接注入域 Bean，这是 A 的真实成本；原卡定的 A→B→C 已修正为 B→A→C，理由：A 会长时间停留在"单体缺 Bean"的中间态，B 是纯加法）；**Step C** —— 单体去数据源 → 停镜像观察 → 删镜像代码 → 备份 → `DROP DATABASE flash_sale`；⓪（历史）**收官日（P4 Day 10，检查表已清零）**：删反向镜像（三个应用的 `module.db.dual-write` 段 + `DualWriteMirror`/`OrderMirror`/`AuthMirror` 等）+ 旧库 `flash_sale` 停写停读并下线 + 决定 `seckill_activity_snapshot`（单体独占、空表）的归属 + 对账脚本退役；建议顺序：先"停镜像观察一轮回归"→ 再删代码 → 最后 `DROP DATABASE flash_sale`（保留一次逻辑备份）；①（可选补证）**客户端 `order-nos` 分支的运行时覆盖**：单体 Redis 是 `database: 1`，用 RESP `SELECT 1` 后 `ZADD seckill:rank:<activityId> <score> <真实有订单的 userId>` → 期望 rank 返回该订单号 → `DEL` 清探针（服务端与契约已于 Day 8 收尾钉死）；②**P4 Day 9 — Redis 隔离**（检查表最后 1 项）：测试不再消费真实 Stream（Testcontainers 或独立 database 索引），做完即可进入收官；②**P4 Day 10（收官）**：删反向镜像 + 旧库 `flash_sale` 下线 + 决定 `seckill_activity_snapshot`（单体独占、表为空）的归属；③可选优化：会话校验 Redis 副本/短 TTL 缓存、T6 — OTel/Zipkin；④图片上传「文件内容与图片格式不匹配」合法图片误拒复现排查仍挂起*：`OrderQueryAdapter`（单体侧 analytics 的 rank/metrics 读路径）从"读 order 副本"改为读 order 服务/事件 —— 这是旧库只剩"三个副本 + 空 snapshot 表"之后最后一条**读**旧库的边；做完才能谈删反向镜像；②之后：**Redis 隔离**（测试不再消费真实 Stream）→ **删反向镜像 + 旧库 `flash_sale` 下线**（三阶段迁移收官）；③可选优化：会话校验的 Redis 副本/短 TTL 缓存（现为每请求一次内部调用）、T6 — OTel/Zipkin 替换自研 traceId；④图片上传「文件内容与图片格式不匹配」合法图片误拒复现排查仍挂起*
