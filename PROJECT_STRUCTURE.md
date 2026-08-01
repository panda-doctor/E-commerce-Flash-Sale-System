# 项目目录结构说明

本文档说明当前工程目录的职责划分。后续开发应优先按本结构放置代码和资源，避免业务逻辑散落。

```text
E-commerceFlashSaleSystem
├── docs
│   ├── project.md
│   ├── developlan.md
│   ├── interface.md
│   └── database.md
├── postman
│   └── test.json
├── deploy
│   ├── mysql
│   │   └── schema.sql
│   └── redis
├── scripts
│   ├── jmeter
│   └── wrk
├── src
│   ├── main
│   │   ├── java/com/ghb/ecommerceflashsalesystem
│   │   │   ├── common
│   │   │   │   ├── api
│   │   │   │   ├── constant
│   │   │   │   ├── exception
│   │   │   │   └── util
│   │   │   ├── config
│   │   │   ├── controller
│   │   │   │   ├── admin
│   │   │   │   ├── health
│   │   │   │   ├── order
│   │   │   │   ├── product
│   │   │   │   ├── rank
│   │   │   │   └── seckill
│   │   │   ├── domain
│   │   │   │   ├── dto
│   │   │   │   │   ├── request
│   │   │   │   │   └── response
│   │   │   │   ├── entity
│   │   │   │   ├── enums
│   │   │   │   └── vo
│   │   │   ├── mapper
│   │   │   ├── service
│   │   │   │   ├── cache
│   │   │   │   ├── impl
│   │   │   │   ├── order
│   │   │   │   ├── product
│   │   │   │   ├── rank
│   │   │   │   ├── ratelimit
│   │   │   │   ├── seckill
│   │   │   │   └── stream
│   │   │   ├── stream
│   │   │   │   ├── consumer
│   │   │   │   └── producer
│   │   │   └── task
│   │   └── resources
│   │       ├── db
│   │       │   └── schema.sql
│   │       ├── lua
│   │       ├── mapper
│   │       ├── static
│   │       │   ├── css
│   │       │   └── js
│   │       └── templates
│   └── test
│       ├── java/com/ghb/ecommerceflashsalesystem
│       │   ├── integration
│       │   └── unit
│       └── resources
└── pom.xml
```

## 目录职责

| 目录 | 职责 |
| --- | --- |
| `docs` | 项目规划、接口文档、数据库设计等说明资料 |
| `postman` | 接口测试集合 |
| `deploy` | 本地部署、数据库初始化、缓存配置等环境文件 |
| `scripts/jmeter` | 压测脚本和压测数据 |
| `scripts/wrk` | 轻量压测脚本 |
| `common/api` | 统一响应体、分页对象、通用请求对象 |
| `common/constant` | 缓存键、消息流、业务常量 |
| `common/exception` | 业务异常和全局异常处理 |
| `common/util` | 编号生成、时间处理、对象转换等工具 |
| `config` | 缓存、数据库、消息队列、跨域等配置 |
| `controller` | 对外接口入口，按业务模块拆分 |
| `domain/entity` | 数据库实体对象 |
| `domain/dto/request` | 请求入参对象 |
| `domain/dto/response` | 响应出参对象 |
| `domain/enums` | 活动状态、订单状态、业务结果枚举 |
| `domain/vo` | 页面或聚合展示对象 |
| `mapper` | 数据库访问接口 |
| `service/cache` | 商品缓存、活动缓存、库存缓存逻辑 |
| `service/ratelimit` | 滑动窗口限流逻辑 |
| `service/seckill` | 秒杀主链路编排 |
| `service/stream` | 消息流写入、确认、重试和死信处理 |
| `stream/producer` | 秒杀订单消息生产者 |
| `stream/consumer` | 秒杀订单消息消费者 |
| `task` | 定时任务，例如消息补偿、指标快照 |
| `resources/db` | 建表脚本和初始数据 |
| `resources/lua` | 库存扣减、限流等脚本 |
| `resources/mapper` | 数据库映射文件 |
| `resources/static` | 简单前端静态资源 |
| `resources/templates` | 页面模板 |
| `test/integration` | 集成测试 |
| `test/unit` | 单元测试 |

## 后续落地顺序

1. 先补齐 `common/api`、`common/exception`、`domain/entity` 和 `mapper`。
2. 再实现 `product` 查询接口和缓存旁路逻辑。
3. 接着实现 `seckill` 活动预热、脚本扣库存和秒杀接口。
4. 然后接入 `stream` 异步下单、订单查询和排行榜。
5. 最后补充压测脚本、运行指标和简单前端页面。

