-- seckill_execute.lua - 原子秒杀执行脚本（幂等 + 库存扣减 + 失败回滚）
--
-- 原子性理由：将"建令牌→检查库存→扣减→失败回滚"放入同一个 Lua 脚本，
-- 避免服务层分步执行时因网络中断/进程崩溃导致令牌残留或库存不一致。
-- 回滚与扣减同生共死，彻底消除中间态。
--
-- KEYS[1] : 库存键 (seckill:stock:{activityId})
-- KEYS[2] : 幂等令牌键 (seckill:user:{activityId}:{userId})
-- ARGV[1] : 令牌值（固定传 "1" 或 userId）
-- ARGV[2] : 令牌 TTL（秒，用 CacheKeyConstant.SECKILL_USER_TOKEN_TTL）
--
-- 返回值：
--   >= 0 : 扣减成功，返回剩余库存
--   -1   : 库存不足或库存键不存在（令牌已回滚）
--   -2   : 令牌已存在（重复秒杀）

local stockKey = KEYS[1]
local tokenKey = KEYS[2]
local tokenValue = ARGV[1] or "1"
local tokenTtl = tonumber(ARGV[2]) or 1800   -- 默认30分钟

-- 1. 检查令牌是否存在
if redis.call("EXISTS", tokenKey) == 1 then
    return -2
    --令牌已存在（重复秒杀）
end

-- 2. 创建令牌（原子 SET NX EX）
local setResult = redis.call("SET", tokenKey, tokenValue, "NX", "EX", tokenTtl)
if not setResult then
    return -2 -- 理论上不会发生（刚才 EXISTS 已检查），但防御
end

--3. 读取库存
local stock = redis.call("GET", stockKey)
if stock == false then
    redis.call("DEL", tokenKey)
    return -1 -- 库存键不存在 → 回滚令牌
end

local stockNum = tonumber(stock)
if stockNum == nil or stockNum <= 0 then
    -- 库存不足或为0 → 回滚令牌
    redis.call("DEL", tokenKey)
    return -1
end

-- 4. 扣减库存
local newStock = redis.call("DECRBY", stockKey, 1)
-- 理论上 newStock >= 0，但若因并发出现负数，也返回 newStock（由业务层决定是否接受）
return newStock