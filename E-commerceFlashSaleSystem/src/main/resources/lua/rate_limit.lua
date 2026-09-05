-- rate_limit.lua - 滑动窗口限流脚本
-- 使用 ZSet 存储请求时间戳，通过 ZREMRANGEBYSCORE 清理窗口外旧数据，ZCARD 统计窗口内请求数。
--
-- 键与参数：
-- KEYS[1] : 限流键 (rate:limit:{userId})
-- ARGV[1] : 当前时间戳（秒，使用 System.currentTimeMillis() / 1000）
-- ARGV[2] : 窗口大小（秒，如 60）
-- ARGV[3] : 阈值（最大请求次数，如 5）
-- ARGV[4] : 唯一成员标识（时间戳 + 随机数，保证不重复）
--
-- 返回值：
--   1 : 放行（请求数未达阈值，已加入窗口）
--   0 : 拒绝（请求数已达阈值）
--
-- 教学注释：
-- 1. ZSet 滑动窗口 vs 固定窗口计数器：
--    固定窗口计数器在窗口切换边界可能瞬间流量突刺（如第59秒和第61秒各请求5次，实际10次跨窗口），
--    滑动窗口通过精确时间范围过滤，边界平滑，能准确限制任意时间窗口内的请求数。
-- 2. member 带随机数的必要性：
--    若 member 仅用时间戳，同一秒内多个请求会因 member 相同而被 ZADD 覆盖，导致统计丢失。
--    加随机后缀 `时间戳-随机数` 保证每个请求的 member 唯一，确保所有请求都被记录。
-- 3. EXPIRE 刷新：
--    每次放行时执行 EXPIRE 刷新键的过期时间，避免活跃用户的键被提前删除，同时不活跃用户的键会自动淘汰。

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local maxCount = tonumber(ARGV[3])
local member = ARGV[4]

-- 1. 清理窗口外的旧成员（score < now - window）
redis.call("ZREMRANGEBYSCORE", key, "-inf", now - window)

-- 2. 统计当前窗口内请求数
local count = redis.call("ZCARD", key)

-- 3. 判断是否超限
if count >= maxCount then
    return 0 -- 拒绝（请求数已达阈值）
end

-- 4. 未超限：加入本次请求，刷新过期时间
redis.call("ZADD", key, now, member)
redis.call("EXPIRE", key, window + 1)   -- 多留一点时间，防止边界过期

return 1   -- 放行
