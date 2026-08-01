# 第一阶段开发计划（day.md）

> 对应阶段：第 1 周 - 地基搭建与基础缓存
> 周期：7 个工作日
> 目标：项目跑通，实现商品缓存旁路模式，解决穿透/击穿/雪崩三大问题

***

## 一、第一阶段核心开发目标

### 1.1 功能模块

| 模块    | 接口/功能                                                     | 说明                  |
| ----- | --------------------------------------------------------- | ------------------- |
| 健康检查  | `GET /api/health`                                         | 检查应用、MySQL、Redis 状态 |
| 商品查询  | `GET /api/products/{productId}`                           | 缓存旁路模式查商品详情         |
| 管理端商品 | `POST /api/admin/products`                                | 创建或更新商品             |
| 管理端活动 | `POST /api/admin/seckill/activities`                      | 创建秒杀活动              |
| 活动预热  | `POST /api/admin/seckill/activities/{activityId}/preheat` | 预热活动+库存到缓存          |
| 活动查询  | `GET /api/seckill/activities/{activityId}`                | 查询活动详情和缓存库存         |
| 活动校验  | `GET /api/seckill/activities/{activityId}/check`          | 校验用户是否可参与           |

### 1.2 数据库表

| 表名                 | 说明     | 阶段 |
| ------------------ | ------ | -- |
| `product`          | 商品基础信息 | 必需 |
| `seckill_activity` | 秒杀活动配置 | 必需 |
| `seckill_order`    | 秒杀订单   | 必需 |

### 1.3 技术依赖清单

| 依赖                             | 版本    | 用途          |
| ------------------------------ | ----- | ----------- |
| JDK                            | 17    | 语言基础        |
| Spring Boot                    | 3.3.4 | 后端框架        |
| Spring Web                     | -     | RESTful API |
| MyBatis-Plus                   | -     | ORM 数据访问    |
| MySQL Connector                | 8.0   | 数据库驱动       |
| Spring Data Redis              | -     | Redis 缓存操作  |
| Redisson                       | -     | 分布式锁        |
| Lombok                         | -     | 减少样板代码      |
| Spring Boot Starter Validation | -     | 参数校验        |
| Spring Boot Starter Test       | -     | 单元测试        |
| Docker                         | 24+   | WSL Docker 运行 Redis 7.0 |
| Docker Compose                 | -     | 未启用（MySQL 本机安装，Redis 用 WSL Docker） |

### 1.4 Redis 缓存键设计（第一阶段）

| 缓存键                                  | 类型     | 说明        | 引入时间  |
| ------------------------------------ | ------ | --------- | ----- |
| `product:detail:{productId}`         | String | 商品详情缓存    | Day 3 |
| `seckill:activity:{activityId}`      | Hash   | 活动信息+时间窗口 | Day 5 |
| `seckill:stock:{activityId}`         | String | 秒杀实时库存    | Day 5 |
| `seckill:user:{activityId}:{userId}` | String | 用户秒杀幂等标记  | Day 5 |

### 1.5 交付标准

- 所有接口通过 Postman 测试集验证
- 缓存旁路模式：首次查库回写缓存，后续命中缓存
- 缓存穿透防护：空值缓存 + 短过期
- 缓存击穿防护：Redisson 互斥锁
- 活动预热：库存预热到 Redis，活动时间窗口可校验
- 单测覆盖率：核心服务层 >= 80%

***

## 二、每日开发计划

***

### Day 1 — 环境搭建与项目初始化

| 项目       | 内容             |
| -------- | -------------- |
| **日期**   | 2026-07-28（周二） |
| **负责人**  | 开发工程师          |
| **交付节点** | 18:00 前        |
| **前置依赖** | 无              |

**任务清单：**

| 序号  | 任务                                                                                                                                 | 工作量  | 产出物                                                    |
| --- | ---------------------------------------------------------------------------------------------------------------------------------- | ---- | ------------------------------------------------------ |
| 1.1 | 搭建运行环境：MySQL 8.0 本机安装 + Redis 7.0（WSL Docker），配置连接与持久化                                                                               | 1.5h | `本机 MySQL + WSL Docker Redis`                            |
| 1.2 | 编写初始化 SQL 脚本（product、seckill\_activity、seckill\_order 三张表 + 索引 + 种子数据）                                                             | 1.5h | `src/main/resources/db/schema.sql`                                |
| 1.3 | 确认 `pom.xml` 依赖完整：spring-boot-starter-web、mybatis-plus、spring-boot-starter-data-redis、redisson、mysql-connector-j、lombok、validation | 0.5h | `pom.xml` 已确认                                          |
| 1.4 | 补齐 `application.yaml`：MySQL 数据源、Redis 连接、MyBatis-Plus 配置、日志级别                                                                | 1h   | `application.yaml`                               |
| 1.5 | 全局配置类骨架：RedisTemplate 序列化配置、Redisson 客户端配置                                                                                         | 1h   | `config/RedisConfig.java`、`config/RedissonConfig.java` |
| 1.6 | 验证环境连通：启动应用后确认 MySQL 和 Redis 可连接                                                                                                   | 0.5h | 控制台日志无连接错误                                             |

**验收标准：**

- MySQL（本机）服务正常，Redis 容器运行中（`docker ps` 可见 redis）
- 应用启动无报错，Spring Boot Banner 正常输出
- `application.yaml` 中数据库和 Redis 配置正确
- Postman 测试 MySQL 和 Redis 连通性（通过后续健康检查接口验证）

**风险预判：**

| 风险                   | 概率 | 影响          | 应对                           |
| -------------------- | -- | ----------- | ---------------------------- |
| Docker 镜像拉取慢         | 中  | 延迟 30-60min | 预先配置国内镜像源，提前拉取               |
| 端口冲突（3306/6379/8080） | 低  | 服务无法启动      | 调整 MySQL 端口或 Redis 容器 `-p` 映射 |
| JDK 版本不匹配            | 低  | 编译失败        | 确认 `JAVA_HOME` 指向 JDK 17     |

***

### Day 2 — 实体模型与数据访问层

| 项目       | 内容             |
| -------- | -------------- |
| **日期**   | 2026-07-29（周三） |
| **负责人**  | 开发工程师          |
| **交付节点** | 18:00 前        |
| **前置依赖** | Day 1（环境就绪）    |

**任务清单：**

| 序号  | 任务                                                                                           | 工作量  | 产出物                      |
| --- | -------------------------------------------------------------------------------------------- | ---- | ------------------------ |
| 2.1 | 创建实体类：`Product`、`SeckillActivity`、`SeckillOrder`，使用 Lombok 注解，字段与数据库列映射                      | 1.5h | `domain/entity/*.java`   |
| 2.2 | 创建状态枚举：`ActivityStatusEnum`、`OrderStatusEnum`、`PreheatStatusEnum`、`ProductStatusEnum`        | 0.5h | `domain/enums/*.java`    |
| 2.3 | 创建 Mapper 接口：`ProductMapper`、`SeckillActivityMapper`、`SeckillOrderMapper`，基础 CRUD + 自定义查询    | 1h   | `mapper/*.java`          |
| 2.4 | 创建 MyBatis XML 映射文件：`ProductMapper.xml`、`SeckillActivityMapper.xml`、`SeckillOrderMapper.xml` | 1h   | `resources/mapper/*.xml` |
| 2.5 | 验证 Mapper 层：编写简单单元测试，测试 CRUD 操作                                                              | 1h   | 单测通过                     |

**验收标准：**

- 实体字段与数据库列一一对应
- 枚举值覆盖文档定义的所有状态
- Mapper 接口基础 CRUD 均可用
- 单元测试中 Insert/Select/Update/Delete 均通过

**风险预判：**

| 风险                                  | 概率 | 影响     | 应对                                  |
| ----------------------------------- | -- | ------ | ----------------------------------- |
| MyBatis-Plus 与 Spring Boot 版本兼容问题 | 低  | 编译失败   | 已规避：采用 Spring Boot 3.3.4（与 MyBatis-Plus 3.5.7 兼容） |
| 枚举与数据库 int 映射错误                     | 中  | 查询结果异常 | 使用 `@EnumValue` 或 `IEnum` 接口处理枚举序列化 |

***

### Day 3 — 统一响应、异常处理与健康检查

| 项目       | 内容                  |
| -------- | ------------------- |
| **日期**   | 2026-07-30（周四）      |
| **负责人**  | 开发工程师               |
| **交付节点** | 18:00 前             |
| **前置依赖** | Day 2（实体+Mapper 就绪） |

**任务清单：**

| 序号  | 任务                                                                                                                                                  | 工作量  | 产出物                                       |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----------------------------------------- |
| 3.1 | 创建统一响应体 `Result<T>`：code、message、data、requestId、timestamp 字段，静态工厂方法 `ok()`、`fail()`                                                                 | 1h   | `common/api/Result.java`                  |
| 3.2 | 创建业务状态码枚举 `ResultCode`：SUCCESS(0)、PARAM\_ERROR(40001)、NOT\_FOUND(40004)、DUPLICATE(40901)、SOLD\_OUT(40902)、RATE\_LIMITED(42900)、SYSTEM\_ERROR(50000) | 0.5h | `common/api/ResultCode.java`              |
| 3.3 | 创建分页响应体 `PageResult<T>`                                                                                                                             | 0.5h | `common/api/PageResult.java`              |
| 3.4 | 创建业务异常 `BusinessException` + 全局异常处理器 `GlobalExceptionHandler`，统一异常返回格式                                                                              | 1h   | `common/exception/*.java`                 |
| 3.5 | 创建请求追踪编号工具类 `RequestIdUtil`                                                                                                                         | 0.5h | `common/util/RequestIdUtil.java`          |
| 3.6 | 实现健康检查控制器：`GET /api/health`，返回应用/MySQL/Redis 状态                                                                                                     | 1h   | `controller/health/HealthController.java` |
| 3.7 | Postman 验证健康检查接口                                                                                                                                    | 0.5h | 接口返回正常                                    |

**验收标准：**

- `GET /api/health` 返回 `{"code":0,"data":{"application":"UP","mysql":"UP","redis":"UP"}}`
- 全局异常处理器捕获所有未处理异常，返回统一格式
- 请求头或拦截器自动注入 `requestId`

**依赖前置条件：**

- Day 2 实体和 Mapper 就绪（健康检查需查数据库验证连通性）
- Redis 配置类就绪（Day 1）

**风险预判：**

| 风险           | 概率 | 影响                 | 应对                                 |
| ------------ | -- | ------------------ | ---------------------------------- |
| Redis 连接配置有误 | 低  | 健康检查 Redis 返回 DOWN | 增加详细错误日志，便于快速定位                    |
| 全局异常未覆盖所有场景  | 中  | 部分异常返回默认错误页        | 添加 `Exception` 兜底捕获 + 404/400 专用处理 |

***

### Day 4 — 商品查询接口与缓存旁路

| 项目       | 内容                 |
| -------- | ------------------ |
| **日期**   | 2026-07-31（周五）     |
| **负责人**  | 开发工程师              |
| **交付节点** | 18:00 前            |
| **前置依赖** | Day 3（统一响应+异常处理就绪） |

**任务清单：**

| 序号  | 任务                                                                                                                                                                    | 工作量  | 产出物                                                                          |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ---------------------------------------------------------------------------- |
| 4.1 | 创建商品 VO：`ProductVO`（含 cacheHit 标记字段）                                                                                                                                  | 0.5h | `domain/vo/ProductVO.java`                                                   |
| 4.2 | 创建商品缓存服务 `ProductCacheService`：`getProductFromCache(productId)`、`setProductToCache(productId, product)`，缓存键 `product:detail:{productId}`，设置随机过期时间（300 + random(60) 秒） | 1.5h | `service/cache/ProductCacheService.java`                                     |
| 4.3 | 创建商品服务接口 `ProductService` + 实现类 `ProductServiceImpl`：`getProductDetail(productId)` 实现缓存旁路模式                                                                           | 1h   | `service/product/ProductService.java`、`service/impl/ProductServiceImpl.java` |
| 4.4 | 创建商品控制器 `ProductController`：实现 `GET /api/products/{productId}`                                                                                                        | 0.5h | `controller/product/ProductController.java`                                  |
| 4.5 | 管理端商品控制器 `AdminProductController`：实现 `POST /api/admin/products`（创建/更新商品，创建后删除缓存）                                                                                      | 1h   | `controller/admin/AdminProductController.java`                               |
| 4.6 | 商品 DTO 校验：`ProductRequest` 使用 `@NotBlank`、`@NotNull`、`@Min` 等注解                                                                                                       | 0.5h | `domain/dto/request/ProductRequest.java`                                     |
| 4.7 | Postman 测试集验证商品查询和管理端接口                                                                                                                                               | 0.5h | 接口返回符合规范                                                                     |

**验收标准：**

- 首次查询商品：日志显示查库，响应 `cacheHit: false`
- 第二次查询同一商品：日志显示查缓存，响应 `cacheHit: true`
- 缓存过期后重新查库回写
- 管理端更新商品后旧缓存被清除
- 查询不存在的商品返回 `code: 40004`

**依赖前置条件：**

- `RedisConfig` 序列化配置就绪（Day 1）
- 统一响应体和异常处理就绪（Day 3）
- Product 实体和 Mapper 就绪（Day 2）

**风险预判：**

| 风险                  | 概率 | 影响         | 应对                               |
| ------------------- | -- | ---------- | -------------------------------- |
| 缓存穿透（恶意请求不存在的商品 ID） | 高  | 频繁查库压垮 DB  | 计划在 Day 5 专门解决，先提 issue 跟踪       |
| Redis 序列化不一致        | 中  | 缓存读写数据格式异常 | 统一使用 Jackson2JsonRedisSerializer |
| 缓存击穿（热点 key 过期）     | 中  | 高并发瞬间打垮 DB | Day 5 引入 Redisson 互斥锁解决          |

***

### Day 5 — 缓存高可用防护（穿透 + 击穿）

| 项目       | 内容              |
| -------- | --------------- |
| **日期**   | 2026-08-01（周六）  |
| **负责人**  | 开发工程师           |
| **交付节点** | 18:00 前         |
| **前置依赖** | Day 4（商品缓存旁路就绪） |

**任务清单：**

| 序号  | 任务                                                                                       | 工作量  | 产出物                                          |
| --- | ---------------------------------------------------------------------------------------- | ---- | -------------------------------------------- |
| 5.1 | 缓存穿透防护：在 `ProductCacheService` 中，对数据库中不存在的商品 ID，在 Redis 中缓存空值（过期时间 60 秒），查询时判断空值标记       | 1h   | `service/cache/ProductCacheService.java`（更新） |
| 5.2 | 缓存击穿防护：在 `ProductServiceImpl.getProductDetail()` 中引入 Redisson 分布式锁，热点 key 过期时只允许一个线程查库回写 | 1.5h | `service/impl/ProductServiceImpl.java`（更新）   |
| 5.3 | 缓存雪崩防护验证：确认商品缓存已使用随机过期时间，避免大量 key 同时过期                                                   | 0.5h | 代码审查确认                                       |
| 5.4 | 管理端商品接口增加参数校验和状态管理                                                                       | 0.5h | `AdminProductController.java`（更新）            |
| 5.5 | 编写商品查询和管理端的 Postman 测试集断言（含边界条件）                                                         | 1h   | postman 测试集更新                                |
| 5.6 | 编写商品服务的单元测试：覆盖查缓存命中、查库回写、空值穿透防护、并发击穿场景                                                   | 1.5h | 单测覆盖                                         |

**验收标准：**

- 查询不存在商品 ID：Redis 缓存空值，后续请求直接返回空值不查库
- 高并发请求同一热点商品：只有一个请求查库，其余等待缓存回写
- 随机过期时间确保多个缓存 key 不会同时过期
- 商品服务和 Mapper 层单测覆盖率 >= 80%

**依赖前置条件：**

- Redisson 配置类就绪（Day 1）
- ProductCacheService 缓存旁路模式就绪（Day 4）
- 统一响应和异常处理就绪（Day 3）

**风险预判：**

| 风险                   | 概率 | 影响     | 应对                                         |
| -------------------- | -- | ------ | ------------------------------------------ |
| Redisson 锁超时导致请求等待过久 | 中  | 接口响应变慢 | 合理设置 `waitTime` 和 `leaseTime`，增加降级策略（直接查库） |
| 空值缓存占用 Redis 内存      | 中  | 内存浪费   | 设置短过期时间（60s），限制空值 key 数量                   |

***

### Day 6 — 活动管理与缓存预热

| 项目       | 内容                          |
| -------- | --------------------------- |
| **日期**   | 2026-08-02（周日）              |
| **负责人**  | 开发工程师                       |
| **交付节点** | 18:00 前                     |
| **前置依赖** | Day 4（缓存服务就绪）、Day 5（缓存防护就绪） |

**任务清单：**

| 序号  | 任务                                                                                                                                              | 工作量  | 产出物                                                                                                                           |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----------------------------------------------------------------------------------------------------------------------------- |
| 6.1 | 创建活动相关 VO/DTO：`SeckillActivityVO`、`ActivityRequest`、`ActivityCheckResponse`                                                                     | 0.5h | `domain/vo/SeckillActivityVO.java`、`domain/dto/request/ActivityRequest.java`、`domain/dto/response/ActivityCheckResponse.java` |
| 6.2 | 创建活动缓存服务 `SeckillCacheService`：`preheatActivity(activityId)` — 将活动信息写入 Hash（`seckill:activity:{id}`）、库存写入 String（`seckill:stock:{id}`）、设置活动 TTL | 2h   | `service/cache/SeckillCacheService.java`                                                                                      |
| 6.3 | 创建活动服务 `SeckillActivityService` + 实现类：活动创建、活动查询（缓存优先）                                                                                           | 1h   | `service/seckill/SeckillActivityService.java`、`service/impl/SeckillActivityServiceImpl.java`                                  |
| 6.4 | 管理端活动控制器：`POST /api/admin/seckill/activities`（创建活动） + `POST /api/admin/seckill/activities/{activityId}/preheat`（预热）                             | 1.5h | `controller/admin/AdminSeckillController.java`                                                                                |
| 6.5 | 活动查询控制器：`GET /api/seckill/activities/{activityId}` + `GET /api/seckill/activities/{activityId}/check?userId=xxx`                                | 1h   | `controller/seckill/SeckillActivityController.java`                                                                           |
| 6.6 | Postman 测试活动创建、预热、查询、校验全流程                                                                                                                      | 0.5h | 测试通过                                                                                                                          |

**验收标准：**

- 创建活动后数据库持久化成功
- 预热后 Redis 中可查到活动 Hash 和库存键
- 预热后查询活动详情返回缓存中的信息
- 校验接口正确判断活动是否进行中、是否售罄
- 未开始/已结束的活动返回对应状态

**依赖前置条件：**

- SeckillActivity 实体和 Mapper 就绪（Day 2）
- 统一响应和异常处理就绪（Day 3）
- Redis 缓存服务基础设施就绪（Day 1）

**风险预判：**

| 风险                | 概率 | 影响        | 应对                                                 |
| ----------------- | -- | --------- | -------------------------------------------------- |
| 活动时间跨时区处理不一致      | 中  | 活动提前或延迟开放 | 统一使用 UTC 时间存储，展示时转换本地时间                            |
| 预热时 Redis key 已存在 | 低  | 库存数据覆盖    | 预热前先判断是否已预热（`preheat_status`），已预热则拒绝重复操作           |
| 活动多状态转换复杂         | 中  | 状态机逻辑混乱   | 明确状态转换图：NOT\_STARTED -> RUNNING -> ENDED/SOLD\_OUT |

***

### Day 7 — 集成测试、Bug 修复与验收

| 项目       | 内容             |
| -------- | -------------- |
| **日期**   | 2026-08-03（周一） |
| **负责人**  | 开发工程师          |
| **交付节点** | 18:00 前        |
| **前置依赖** | Day 1\~6 全部完成  |

**任务清单：**

| 序号  | 任务                                                                         | 工作量  | 产出物                                                        |
| --- | -------------------------------------------------------------------------- | ---- | ---------------------------------------------------------- |
| 7.1 | 执行全量 Postman 测试集：健康检查 + 商品管理 + 商品查询 + 活动管理 + 活动预热 + 活动查询 + 活动校验，共 7 个接口全覆盖 | 1h   | 全部通过                                                       |
| 7.2 | 集成测试：启动完整链路（创建商品 -> 创建活动 -> 预热 -> 查询缓存 -> 校验活动），验证数据一致性                    | 1.5h | `src/test/java/.../integration/Phase1IntegrationTest.java` |
| 7.3 | 缓存穿透集成测试：连续请求不存在的商品 ID 100 次，观察 DB 查询次数（应为 1 次）                            | 0.5h | 集成测试通过                                                     |
| 7.4 | 缓存击穿集成测试：多线程并发请求同一商品，观察 DB 查询次数（应为 1 次）                                    | 0.5h | 集成测试通过                                                     |
| 7.5 | 修复测试中发现的 Bug，补充缺失的边界处理                                                     | 1.5h | Bug 修复完毕                                                   |
| 7.6 | 确认 `application.yaml` 中生产级配置（连接池、超时时间、日志级别）                          | 0.5h | 配置复查通过                                                     |
| 7.7 | 输出第一阶段验收报告，标记完成与待优化项                                                       | 0.5h | `docs/phase1-review.md`                                    |

**验收标准：**

- 所有接口响应格式符合 interface.md 规范
- 缓存旁路模式：第 2 次起命中缓存
- 缓存穿透防护：不存在 ID 查库次数 <= 1
- 缓存击穿防护：并发下查库次数 <= 1
- 活动预热：预热后缓存数据与数据库一致
- 单元测试 + 集成测试全部通过

**风险预判：**

| 风险            | 概率 | 影响     | 应对                        |
| ------------- | -- | ------ | ------------------------- |
| 前一天任务延期未完成    | 中  | 验收受阻   | 优先级排序，核心接口优先验收            |
| 集成测试发现跨模块 Bug | 中  | 需要联调修复 | 设 3h 缓冲时间，超时则标记为第二阶段前置修复项 |
| 缓存与数据库数据不一致   | 低  | 展示错误数据 | 增加缓存更新策略（更新 DB 后立即清除缓存）   |

***

## 三、第一阶段工作分解结构（WBS）

```
Phase 1 — 地基搭建与基础缓存
├── 1. 环境搭建（Day 1）
│   ├── 1.1 搭建 MySQL（本机）+ Redis（WSL Docker）
│   ├── 1.2 初始化建表脚本
│   ├── 1.3 Maven 依赖确认
│   ├── 1.4 application.yaml 配置
│   └── 1.5 Redis/Redisson 配置类
├── 2. 数据访问层（Day 2）
│   ├── 2.1 实体类（3 个）
│   ├── 2.2 状态枚举
│   ├── 2.3 Mapper 接口（3 个）
│   └── 2.4 MyBatis XML 映射
├── 3. 公共组件 + 健康检查（Day 3）
│   ├── 3.1 统一响应体 / 分页响应
│   ├── 3.2 业务状态码
│   ├── 3.3 全局异常处理
│   └── 3.4 健康检查接口
├── 4. 商品服务 + 缓存旁路（Day 4-5）
│   ├── 4.1 商品缓存服务
│   ├── 4.2 商品查询接口（缓存旁路）
│   ├── 4.3 管理端商品接口
│   ├── 4.4 缓存穿透防护
│   └── 4.5 缓存击穿防护（分布式锁）
├── 5. 活动管理 + 预热（Day 6）
│   ├── 5.1 活动缓存服务
│   ├── 5.2 活动创建接口
│   ├── 5.3 活动预热接口
│   ├── 5.4 活动查询接口
│   └── 5.5 活动校验接口
└── 6. 联调验收（Day 7）
    ├── 6.1 全量接口 Postman 验证
    ├── 6.2 集成测试
    ├── 6.3 Bug 修复
    └── 6.4 验收报告
```

***

## 四、依赖关系图

```
Day 1 (环境) ───────── Day 2 (数据层) ───── Day 3 (公共组件) ──── Day 4 (商品缓存)
       │                      │                     │                    │
       │                      │                     └── 依赖 ───────────┤
       │                      └──────────────────── 依赖 ───────────────┤
       └───────────────────────────────────────────────────────────── 依赖 ─┘
                                                                          │
                                                    Day 6 (活动管理) ←───┤
                                                         │               │
                                                    Day 5 (缓存防护) ←───┘
                                                         │
                                                    Day 7 (联调验收)
```

**关键路径**：Day 1 -> Day 2 -> Day 3 -> Day 4 -> Day 5 -> Day 7

**可并行路径**：Day 6 可与 Day 5 并行开发（但依赖 Day 4 的缓存服务）

***

## 五、风险预判与应对预案

### 5.1 技术风险

| 风险项                                | 概率 | 影响级别 | 触发条件               | 应对方案                                          |
| ---------------------------------- | -- | ---- | ------------------ | --------------------------------------------- |
| Spring Boot 4.x 与 MyBatis-Plus 兼容性 | 低  | 高    | 编译期报错              | 已规避：实际采用 Spring Boot 3.3.4                    |
| Redisson 与 Spring Boot 版本不匹配    | 低  | 中    | 启动报错               | 已规避：使用 Redisson 核心库 3.34.0（兼容 Boot 3.3.4） |
| Redis 缓存穿透攻击                       | 高  | 中    | 恶意请求大量不存在 ID       | 布隆过滤器（阶段二引入），当前用空值缓存兜底                        |
| 缓存与数据库数据不一致                        | 中  | 中    | 并发更新+删除缓存竞态        | 采用"延迟双删"策略或 Canal 订阅 binlog（阶段二优化）            |
| 本地 Docker 资源不足                     | 低  | 中    | MySQL/Redis 容器 OOM | 缩小容器内存限制，或直接使用本地安装的 MySQL/Redis               |

### 5.2 进度风险

| 风险项          | 概率 | 影响     | 应对                          |
| ------------ | -- | ------ | --------------------------- |
| Day 任务延期 1 天 | 中  | 连锁反应   | Day 6 或 Day 7 有 1 天缓冲，可消化延期 |
| 多任务延期超过 2 天  | 低  | 影响整体进度 | 裁剪 Day 7 非核心内容，核心接口优先验收     |
| 需求变更或范围扩大    | 低  | 进度推迟   | 新需求记入二期 backlog，本期冻结范围      |
| 主要人员请假       | 低  | 任务停滞   | 关键节点提前完成，留文档化交接材料           |

### 5.3 质量风险

| 风险项       | 应对                                       |
| --------- | ---------------------------------------- |
| 单测覆盖率不达标  | Day 7 集中补充核心链路单测，Mapper 层和 Service 层必须覆盖 |
| 缓存逻辑验证不充分 | 集成测试中增加并发场景（CyclicBarrier 模拟多线程）         |
| 接口响应格式不一致 | 全局异常处理 + AOP 响应拦截保证格式统一                  |

### 5.4 兜底方案

若出现严重延期，按以下优先级裁剪：

| 优先级      | 保留内容                 | 可裁剪/推迟内容         |
| -------- | -------------------- | ---------------- |
| P0（必须交付） | 健康检查、商品查询（缓存旁路）、商品创建 | -                |
| P1（重要）   | 活动创建、活动预热、缓存穿透防护     | -                |
| P2（可推迟）  | 缓存击穿防护、活动校验接口        | 推迟至第二阶段 Day 1 补齐 |
| P3（锦上添花） | 集成测试、验收报告            | 可简化或跳过           |

***

## 六、交付物清单

| 序号 | 交付物                                                                     | 来源    | 验收方式                     |
| -- | ----------------------------------------------------------------------- | ----- | ------------------------ |
| 1  | 本机 MySQL 8.0 + WSL Docker Redis 7.0（无 Compose 编排）                     | Day 1 | MySQL/Redis 可连接              |
| 2  | `src/main/resources/db/schema.sql`（5 张表 + 种子数据）                      | Day 1 | 表结构+种子数据正确               |
| 3  | `src/main/resources/application.yaml`                                    | Day 1 | 配置完整可启动                  |
| 4  | `config/RedisConfig.java`                                               | Day 1 | RedisTemplate Bean 注入成功  |
| 5  | `config/RedissonConfig.java`                                            | Day 1 | RedissonClient Bean 注入成功 |
| 6  | `domain/entity/Product.java`、`SeckillActivity.java`、`SeckillOrder.java` | Day 2 | 字段映射正确                   |
| 7  | `domain/enums/*.java`                                                   | Day 2 | 枚举值完整                    |
| 8  | `mapper/*.java` + `resources/mapper/*.xml`                              | Day 2 | CRUD 测试通过                |
| 9  | `common/api/Result.java`、`ResultCode.java`、`PageResult.java`            | Day 3 | 响应格式一致                   |
| 10 | `common/exception/BusinessException.java`、`GlobalExceptionHandler.java` | Day 3 | 异常统一处理                   |
| 11 | `controller/health/HealthController.java`                               | Day 3 | 返回三端状态                   |
| 12 | `service/cache/ProductCacheService.java`                                | Day 4 | 缓存读写正确                   |
| 13 | `service/product/ProductService.java` + `impl`                          | Day 4 | 缓存旁路模式                   |
| 14 | `controller/product/ProductController.java`                             | Day 4 | 接口响应符合规范                 |
| 15 | `controller/admin/AdminProductController.java`                          | Day 4 | 商品 CRUD                  |
| 16 | 缓存穿透/击穿防护代码                                                             | Day 5 | 并发测试验证                   |
| 17 | `service/cache/SeckillCacheService.java`                                | Day 6 | 预热功能                     |
| 18 | `service/seckill/SeckillActivityService.java` + `impl`                  | Day 6 | 活动 CRUD                  |
| 19 | `controller/admin/AdminSeckillController.java`                          | Day 6 | 管理端活动接口                  |
| 20 | `controller/seckill/SeckillActivityController.java`                     | Day 6 | 活动查询+校验接口                |
| 21 | `src/test/java/.../integration/Phase1IntegrationTest.java`              | Day 7 | 集成测试通过                   |
| 22 | `docs/phase1-review.md`                                                 | Day 7 | 验收报告                     |

***

## 七、附录

### 7.1 参考文档

- [project.md](project.md) — 项目概述与 Redis 方案映射
- [developlan.md](developlan.md) — 4 周开发计划
- [interface.md](interface.md) — 接口规范（阶段一接口：4.1/4.2/4.3/4.4/4.5/4.6/4.7）
- [database.md](database.md) — 数据库设计（3 张主表）
- [pom.xml](../E-commerceFlashSaleSystem/pom.xml) — Maven 依赖
- [PROJECT\_STRUCTURE.md](../E-commerceFlashSaleSystem/PROJECT_STRUCTURE.md) — 项目目录结构

### 7.2 快速参考：缓存的三种问题与解决方案

| 问题   | 现象                  | 解决方案         | 引入时间  |
| ---- | ------------------- | ------------ | ----- |
| 缓存穿透 | 查询不存在的数据，每次都查 DB    | 空值缓存（短过期）    | Day 5 |
| 缓存击穿 | 热点 key 过期，高并发全打到 DB | 分布式锁互斥更新     | Day 5 |
| 缓存雪崩 | 大量 key 同时过期         | 随机过期时间（基础防御） | Day 4 |

