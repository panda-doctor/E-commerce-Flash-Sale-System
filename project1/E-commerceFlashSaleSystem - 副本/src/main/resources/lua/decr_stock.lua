-- 库存扣减脚本
-- KEYS[1]: 库存键 (seckill:stock:{activityId})
-- ARGV[1]: 扣减数量（默认为1）
-- 返回值：
--   >= 0 : 扣减后的剩余库存
--   -1   : 库存不足、库存已耗尽或键不存在

local stockKey = KEYS[1]
local decrAmount = tonumber(ARGV[1]) or 1

-- 1. 获取当前库存
local current = redis.call("GET", stockKey)
if current == false then
    -- 键不存在，返回 -1
    return -1
end

local stock = tonumber(current)
if stock == nil then
    -- 无法转换为数字（异常情况），返回 -1
    return -1
end

-- 2. 判断库存是否足够
if stock <= 0 then
    return -1
end

-- 3. 扣减库存（DECRBY 会返回扣减后的值）
local newStock = redis.call("DECRBY", stockKey, decrAmount)
return newStock