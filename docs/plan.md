# 项目开发计划与进度追踪

> 本文档记录项目开发过程中的进度、已完成工作、待办事项以及经验教训。

---

## 一、项目基本信息

| 项目 | 内容 |
|------|------|
| 项目名称 | 轻量级电商秒杀系统（E-commerce Flash Sale System） |
| 技术栈 | Spring Boot 3.3.4 / JDK 17 / MySQL 8.0 + MyBatis-Plus 3.5.7 / Redis 7.0 + Redisson 3.34.0 |
| 项目文档 | `docs/project.md`、`docs/developlan.md`、`docs/interface.md`、`docs/database.md`、`docs/day.md` |
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

---

## 二、整体路线图

```
■ 第 1 阶段：地基搭建与基础缓存（Day 1 ~ Day 7）
  ✅ Day 1 环境搭建  ✅ Day 2 实体与数据层  ✅ Day 3 公共组件
  ▶ Day 4 商品缓存  ⏳ Day 5 缓存防护    ⏳ Day 6 活动管理
  ⏳ Day 7 集成验收
□ 第 2 阶段：秒杀核心与原子库存扣减
□ 第 3 阶段：高并发防护体系
□ 第 4 阶段：排行榜、前端与全链路压测
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

### Day 4 — 商品查询接口与缓存旁路 ▶ 进行中（2026-08-01）

| 任务 | 状态 | 说明 |
|------|------|------|
| 4.1 商品视图对象 `ProductVO` | ⏳ | `domain/vo/ProductVO.java`，含 `cacheHit` 标记字段，status 用 `ProductStatusEnum` |
| 4.2 商品缓存服务 `ProductCacheService` | ⏳ | 缓存键 `product:detail:{productId}`，随机过期 300+random(60) 秒，`getProductFromCache` / `setProductToCache` |
| 4.3 商品服务 `ProductService` + `Impl` | ⏳ | 缓存旁路：先查缓存 → 未命中查库 → 回写缓存 → 返回，带 cacheHit 标记与查库/查缓存日志 |
| 4.4 商品查询控制器 `ProductController` | ⏳ | `GET /api/products/{productId}`，商品不存在返回 40004 |
| 4.5 管理端控制器 `AdminProductController` | ⏳ | `POST /api/admin/products` 创建/更新商品，写操作后删除对应缓存 |
| 4.6 商品请求 DTO `ProductRequest` | ⏳ | `@NotBlank` / `@NotNull` / `@Min` 校验，productId 可选（创建/更新判定） |
| 4.7 接口验证与测试 | ⏳ | 首查 cacheHit=false、二次 cacheHit=true、更新清缓存、不存在 40004；`mvn test` 保证 BUILD SUCCESS |

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

**Day 4 经验教训：**（开发过程中补充）

### 后续任务计划（Day 4 ~ Day 7）

| 天 | 主要内容 | 前置依赖 |
|----|----------|----------|
| Day 4 ▶ | 商品缓存旁路、商品查询接口、管理端商品接口 | Day 3 ✅ |
| Day 5 | 缓存穿透防护、缓存击穿防护（分布式锁）、单测 | Day 4 |
| Day 6 | 活动管理、缓存预热、活动查询与校验接口 | Day 4 |
| Day 7 | 集成测试、问题修复、阶段验收 | Day 5 + Day 6 |

---

## 四、数据库表结构参考

### 当前阶段使用的表

| 表名 | 实体类 | Mapper |
|------|--------|--------|
| `product` | `Product.java` | `ProductMapper.java` |
| `seckill_activity` | `SeckillActivity.java` | `SeckillActivityMapper.java` |
| `seckill_order` | `SeckillOrder.java` | `SeckillOrderMapper.java` |

### 后续阶段使用的表

| 表名 | 阶段 | 说明 |
|------|------|------|
| `seckill_message_log` | 第 2-3 阶段 | 消息消费日志 |
| `seckill_activity_snapshot` | 第 3-4 阶段 | 运行指标快照 |

---

## 五、Redis 缓存键设计（当前阶段）

| 缓存键 | 类型 | 用途 | 引入阶段 |
|--------|------|------|----------|
| `product:detail:{productId}` | String | 商品详情缓存旁路 | Day 4 ▶ |
| `seckill:activity:{activityId}` | Hash | 活动信息+时间窗口 | Day 6 |
| `seckill:stock:{activityId}` | String | 秒杀实时库存 | Day 6 |
| `seckill:user:{activityId}:{userId}` | String | 用户秒杀幂等标记 | Day 6 |

---

## 六、接口状态（当前阶段）

| 方法 | 路径 | 状态 | 计划实现日 |
|------|------|------|-----------|
| GET | `/api/health` | ✅ | Day 3 |
| GET | `/api/products/{productId}` | ▶ | Day 4 |
| POST | `/api/admin/products` | ▶ | Day 4 |
| POST | `/api/admin/seckill/activities` | ⏳ | Day 6 |
| POST | `/api/admin/seckill/activities/{id}/preheat` | ⏳ | Day 6 |
| GET | `/api/seckill/activities/{id}` | ⏳ | Day 6 |
| GET | `/api/seckill/activities/{id}/check` | ⏳ | Day 6 |

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

*文档创建日期：2026-07-29*
*上次更新：2026-08-01（Day 4 开发进行中）*
*下次开始位置：Day 4 — 任务 4.1（商品缓存旁路 ProductCacheService）*
