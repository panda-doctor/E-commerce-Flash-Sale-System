package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(properties = "flash.stream.auto-poll=false")
public class SeckillExecuteScriptTest {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisScript<Long> seckillExecuteScript;

    private static final Long ACTIVITY_ID = 100L;
    private static final Long USER_ID = 1001L;
    private String stockKey;
    private String tokenKey;

    @BeforeEach
    void setUp() {
        // 清理相关键
        stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        tokenKey = CacheKeyConstant.SECKILL_USER_PREFIX + ACTIVITY_ID + ":" + USER_ID;
        cleanKeys(stockKey, tokenKey);
    }

    @AfterEach
    void tearDown() {
        cleanKeys(stockKey, tokenKey);
    }

    private void cleanKeys(String ... keys) {
        for ( String key : keys) {
            redisTemplate.delete(key);
        }
    }

    // 场景1：正常扣减
    @Test
    void testScriptSuccess() {
        // 预设库存 10
        redisTemplate.opsForValue().set(stockKey, 10);

        Long result = redisTemplate.execute(seckillExecuteScript,
                Arrays.asList(stockKey, tokenKey),
                "1", "1800");
        assertThat(result).isEqualTo(9L);

        // 令牌存在并且 ttl > 0
        assertThat(redisTemplate.hasKey(tokenKey)).isTrue();
        Long ttl = redisTemplate.getExpire(tokenKey);
        assertThat(ttl).isGreaterThan(0);
    }

    // 2. 重读调用返回 -2
    @Test
    void testScriptDuplicate() {
        // 1.先建令牌(模拟首次成功)
        redisTemplate.opsForValue().set(tokenKey, "1");

        //2.库存预设
        redisTemplate.opsForValue().set(stockKey, 10);

        Long result = redisTemplate.execute(
                seckillExecuteScript,
                Arrays.asList(stockKey, tokenKey),
                "1", "1800"
        );
        assertThat(result).isEqualTo(-2L);
        //3.重复秒杀不清令牌（保留原令牌）
        assertThat(redisTemplate.hasKey(tokenKey)).isTrue();
    }

    // 3. 库存为0， 返回-1 并且回滚令牌
    // 注意：此处【不能】预先创建令牌——若令牌已存在，脚本第一步 EXISTS 就会返回 -2（重复秒杀），
    // 走不到库存检查。正确姿势：不预建令牌，让脚本自己"建令牌 -> 发现库存 0 -> 回滚令牌 -> 返回 -1"。
    @Test
    void testScriptSoldOut() {
        //库存预设为 0
        redisTemplate.opsForValue().set(stockKey, 0);

        Long result = redisTemplate.execute(
                seckillExecuteScript,
                Arrays.asList(stockKey, tokenKey),
                "1", "1800"
        );
        assertThat(result).isEqualTo(-1L);

        //令牌应被脚本回滚删除
        assertThat(redisTemplate.hasKey(tokenKey)).isFalse();
    }
}
