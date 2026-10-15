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
  ✅ P0 边界治理（模块化单体，M1 达成 2026-10-13）  🔄 P1 基础设施（P1-1 ✅；P1-2 T1~T4 ✅）  ⏳ P2 边缘抽取  ⏳ P3 商品与活动
  ⏳ P4 核心交易链路  ⏳ P5 读模型收口  ⏳ P6 质量与运维
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

## 八、第 5 阶段：微服务架构演进（规划中 · 未开工）

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
| P4 | 核心交易（seckill / order / `order.created` 事件 / 数据迁移） | 6 天 | M5 跨服务交易闭环 | ⏳ |
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
| Day 2 | product 域割接：controllers + FileStorageWebConfig 迁入应用；单体摘除；网关导流 `/api/products/**`、`/api/admin/products/**`、`/api/admin/files/**`、`/uploads/**` | ⏳ | 经网关商品/上传链路可用；直连单体同路径 404 |
| Day 3 | 数据所有权：product / activity 独立 schema（DDL + 迁移脚本）+ 双写开关 + 对账脚本 | ⏳ | 两库行数/金额对账一致 |
| Day 4 | activity 域服务化（:8085，同「库 + 应用」模式）+ 网关导流 + 预热锁归属 activity | ⏳ | 预热并发用例绿；经网关活动链路可用 |
| Day 5 | seckill 提供内部「库存初始化/重置」接口（供 activity 编排）+ 全量回归 + 收口 | ⏳ | 接口收敛后回归绿线；文档同步 |

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

---

*文档创建日期：2026-07-29*
*上次更新：2026-10-14（**P1-2 收尾完成：T1~T5 + 全量绿线**：Nacos 2.5.4 本机 standalone（单体注册 `flash-sale-monolith`，healthy）；网关 `lb://` 服务发现路由；**T5 观测栈**（WSL `redis` 发行版 Docker，Prometheus v2.53.0 + Grafana 11.1.0，targets 3/3 UP、数据源与看板 provisioning、6 面板出图，提交 `289e7e3`）；**停应用后全量回归 `mvnw clean test` → 113 用例 0 失败 BUILD SUCCESS（1.1 分钟，14 模块全绿）**；环境级结论：观测栈须 `network_mode: host`、Windows 侧访问须 `127.0.0.1`、观测栈归属 WSL `redis` 发行版且重启后需唤醒；`deploy/README.md` 含完整观测栈说明与排查。另修复阻塞性环境问题：Maven 镜像 `http → https`；**P2 Day 1 完成**（`flash-sale-auth` 可启动骨架：`:8082` health UP、Nacos 服务列表 3 个、零行为变更）；**P2 Day 2 完成**（`flash-sale-auth-core` 库抽取 + 单体摘除 auth 域 + 网关精确路由；经网关 auth 链路 `code=0`、带有效凭证直连单体 404、其余业务仍自持鉴权；install exit=0 / core 单测 13/13 / ArchitectureTest 16/16）；**P2 Day 3~4 完成并收口**（ai 服务化 :8083 + 网关准入鉴权 + 全量 **124 用例**绿线 + `p2-done` 标签；提交 `1014a8e`/`7db07eb`/`4275ba2`）；**P3 Day 1 完成**（product 域拆为 `flash-sale-product-core` 库 + `flash-sale-product` 应用 :8084；零行为变更 —— 网关路由未改、停 8084 后经网关商品接口仍 200；Nacos 5 服务；全量 124 用例绿线；改名提交 `3afc88b`））*
*下次开始位置：①**P3 Day 2 — product 域割接**（controllers 3 个 + `FileStorageWebConfig` + 上传/存储适配迁入 `flash-sale-product` 应用、单体摘除该域、网关新增精确路由 `/api/products/**`、`/api/admin/products/**`、`/api/admin/files/**`、`/uploads/**`（置于兜底前）；**摘除与导流必须同批**，避免"接口已摘除但未导流"的破窗态）；②P3 Day 3 — 数据所有权（product / activity 独立 schema + 双写开关 + 对账脚本）；③可选 T6 — OTel/Zipkin 替换自研 traceId；④图片上传「文件内容与图片格式不匹配」合法图片误拒复现排查仍挂起*
