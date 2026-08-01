# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

轻量级电商秒杀系统 — an educational e-commerce flash sale (seckill) system demonstrating Redis enterprise patterns in high-concurrency scenarios. Uses Redis for caching, atomic inventory deduction, distributed locking, sliding-window rate limiting, async order processing via Redis Streams, and real-time leaderboards.

**Stack:** Spring Boot 4.1.0 / JDK 17 / MySQL 8.0 + MyBatis-Plus 3.5.7 / Redis 7.0 + Redisson 3.34.0

## Build & Run

```bash
# Build the project (Maven wrapper)
./mvnw clean install

# Run the app
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ECommerceFlashSaleSystemApplicationTests

# Package
./mvnw clean package

# Start infrastructure (MySQL + Redis)
docker compose -f deploy/docker-compose.yml up -d
```

## Project Structure

```
E-commerceFlashSaleSystem/
├── deploy/                         # Docker Compose, SQL init scripts
├── docs/                           # project.md, interface.md, database.md, developlan.md, day.md
├── postman/                        # API test collection
├── scripts/jmeter/                 # Load testing scripts
├── src/main/java/.../
│   ├── common/api/                 # Result<T>, ResultCode, PageResult
│   ├── common/constant/            # Cache/stream keys, biz constants
│   ├── common/exception/           # BusinessException, GlobalExceptionHandler
│   ├── common/util/                # RequestIdUtil, ID generators, etc.
│   ├── config/                     # RedisConfig, RedissonConfig
│   ├── controller/                 # admin/, health/, order/, product/, rank/, seckill/
│   ├── domain/dto/request/         # Request DTOs with validation
│   ├── domain/dto/response/        # Response DTOs
│   ├── domain/entity/              # DB entities (Product, SeckillActivity, SeckillOrder)
│   ├── domain/enums/               # ActivityStatus, OrderStatus, ProductStatus, PreheatStatus
│   ├── domain/vo/                  # View objects (ProductVO with cacheHit flag)
│   ├── mapper/                     # MyBatis-Plus mappers
│   ├── service/cache/              # ProductCacheService, SeckillCacheService
│   ├── service/impl/               # Service implementations (ProductServiceImpl, etc.)
│   ├── service/order/              # Order service interfaces
│   ├── service/product/            # Product service interface
│   ├── service/rank/               # Leaderboard service
│   ├── service/ratelimit/          # Sliding window rate limiter
│   ├── service/seckill/            # Flash sale orchestration service
│   ├── service/stream/             # Stream message service
│   ├── stream/consumer/            # Redis Stream consumers (async order creation)
│   ├── stream/producer/            # Redis Stream producers
│   └── task/                       # Scheduled tasks (message compensation, metrics snapshots)
├── src/main/resources/
│   ├── db/schema.sql               # Full schema + seed data
│   ├── lua/                        # Lua scripts (inventory decr, rate limiter)
│   ├── mapper/                     # MyBatis XML mappers
│   ├── static/css/, js/            # Frontend assets
│   └── templates/                  # Thymeleaf templates
└── src/test/java/.../
    ├── integration/                # Integration tests
    └── unit/                       # Unit tests
```

## Architecture & Redis Patterns

The core flash sale pipeline follows this order in `/api/seckill/execute`:

```
Request → Sliding window rate limit (ZSet)
       → Activity time check (Hash cache)
       → User dedup/idempotency (SETNX + distributed lock)
       → Atomic inventory deduction (Lua script)
       → Write to Redis Stream
       → Return "QUEUED" to user
       → Async consumer creates order in MySQL
       → On success: ZINCRBY leaderboard
```

### Redis Key Patterns

| Key Pattern | Type | Purpose |
|---|---|---|
| `product:detail:{productId}` | String | Product detail cache (cache-aside) |
| `seckill:activity:{activityId}` | Hash | Activity info + time window |
| `seckill:stock:{activityId}` | String | Flash sale stock (atomic decr via Lua) |
| `seckill:user:{activityId}:{userId}` | String | User purchase idempotency token (SETNX) |
| `rate:limit:{userId}` | ZSet | Sliding window rate limiter |
| `seckill:order:stream` | Stream | Flash sale order message queue |
| `seckill:order:dead:stream` | Stream | Dead-letter stream for failed messages |
| `seckill:lock:{userId}:{goodsId}` | Lock | Redisson distributed lock |
| `seckill:rank:{activityId}` | ZSet | Real-time leaderboard |

### Solution Mapping

| Problem | Redis Solution | Implementation |
|---|---|---|
| High-concurrency reads | Cache-aside with random TTL | ProductCacheService, Random expiration (300+random(60)s) |
| Cache penetration | Null-value cache (short TTL) | ProductCacheService puts empty marker for 60s |
| Cache breakdown (hot key) | Redisson mutex lock | Only one thread reloads from DB |
| Inventory oversell | Lua atomic script | Single Redis eval: check stock > 0 then decr |
| Duplicate purchases | SETNX token + Redisson lock | `seckill:user:{activityId}:{userId}` key |
| Traffic spike | Redis Stream async queue | Producer writes, consumer group processes |
| Rate limiting | Sliding window ZSet | ZREMRANGEBYSCORE + ZCARD per userId window |
| Real-time leaderboard | Sorted Set | ZINCRBY on order success, ZREVRANGE for top 10 |

### Database Tables (5)

- `product` — Product catalog (seckill price lives in activity, not here)
- `seckill_activity` — Activity config (time window, stock, price, preheat status, version for optimistic lock)
- `seckill_order` — Orders (unique key `uk_activity_user` guarantees DB-level dedup)
- `seckill_message_log` — Stream message consumption tracking (retry/dead-letter support)
- `seckill_activity_snapshot` — Periodic metrics snapshots for load testing analysis

### API Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | App + MySQL + Redis health check |
| GET | `/api/products/{id}` | Product detail (cache-aside) |
| POST | `/api/admin/products` | Create/update product |
| POST | `/api/admin/seckill/activities` | Create flash sale activity |
| POST | `/api/admin/seckill/activities/{id}/preheat` | Preheat activity to Redis cache |
| GET | `/api/seckill/activities/{id}` | Query activity details |
| GET | `/api/seckill/activities/{id}/check` | Check user eligibility |
| POST | `/api/seckill/execute` | **Core:** execute flash sale |
| GET | `/api/seckill/orders/{orderNo}` | Query order status |
| GET | `/api/seckill/users/{userId}/orders` | Query user flash sale orders |
| GET | `/api/rank/top10` | Flash sale leaderboard |
| GET | `/api/admin/seckill/activities/{id}/metrics` | Activity runtime metrics |

### Business Status Codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 40001 | Parameter error |
| 40004 | Resource not found |
| 40901 | Duplicate flash sale |
| 40902 | Out of stock |
| 42900 | Rate limited |
| 50000 | System error |

## Development Phases

The project is implemented in 4 incremental phases following `docs/developlan.md`:

1. **Phase 1 (Week 1):** Foundation — cache-aside, penetration/breakdown/snow protection, activity preheat
2. **Phase 2 (Week 2):** Core seckill — Lua atomic stock deduction, SETNX dedup, JMeter validation
3. **Phase 3 (Week 3):** High-concurrency defense — distributed lock, sliding window rate limit, Redis Stream async ordering
4. **Phase 4 (Week 4):** Leaderboard, frontend, end-to-end load testing

The day-by-day plan is documented in `docs/day.md` (currently at Phase 1, Day 1).

## Important Conventions

- **Price:** stored in 分 (cents) as `BIGINT` to avoid float errors
- **Time:** UTC storage, Asia/Shanghai display (`yyyy-MM-dd HH:mm:ss`)
- **IDs:** assigned by the application (not auto-increment) via `id-type: INPUT` in MyBatis-Plus config
- **Cache TTL:** base + random jitter to prevent cache avalanche (e.g., 300 + random(60) seconds)
- **Request format:** `application/json` for all APIs
- **Response format:** unified `Result<T>` wrapper with `code`, `message`, `data`, `requestId`, `timestamp`
- **Lua scripts:** placed in `src/main/resources/lua/` and loaded as `RedisScript<Long>`
- **MyBatis XML mappers:** placed in `src/main/resources/mapper/` with `classpath:mapper/*.xml` location

## Key Dependencies (pom.xml)

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-webmvc | 4.1.0 | REST API |
| mybatis-plus-spring-boot3-starter | 3.5.7 | ORM |
| redisson-spring-boot-starter | 3.34.0 | Distributed locks |
| spring-boot-starter-data-redis | 4.1.0 | Redis template + Lettuce |
| mysql-connector-j | 8.0.33 | MySQL driver |
| jackson-datatype-jsr310 | - | Java 8 time serialization |
| spring-boot-starter-validation | 4.1.0 | Bean validation |
| lombok | - | Boilerplate reduction |
