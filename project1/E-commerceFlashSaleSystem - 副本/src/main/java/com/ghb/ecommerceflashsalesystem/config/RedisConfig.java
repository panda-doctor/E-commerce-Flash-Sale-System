package com.ghb.ecommerceflashsalesystem.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory
                                                     , ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key 使用 String 序列化
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());

//        // value 使用 Jackson JSON 序列化（Spring Data Redis 4.x 推荐方式）
//        // 注意：GenericJackson2JsonRedisSerializer 已在 Spring Data Redis 4.x 中废弃并计划删除
//        template.setValueSerializer(RedisSerializer.json());
//        template.setHashValueSerializer(RedisSerializer.json());

// ② 复制 ObjectMapper 并开启默认类型支持
        ObjectMapper mapper = objectMapper.copy();
        // S2 加固：DefaultTyping 开启后，反序列化会按 JSON 里的 @class 字段实例化类。
        // 若继续用 LaissezFaireSubTypeValidator（放任一切类型），一旦 Redis 数据被污染，
        // 可能被引导实例化任意 gadget 类。改用 BasicPolymorphicTypeValidator 白名单：
        // 仅放行本工程实体包 + 缓存可能写入的常用 JDK 类型（时间/容器/数值/网络），其余一律拒绝。
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.ghb.ecommerceflashsalesystem.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.net.")
                .build();
        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
// ③ 用这个 ObjectMapper 构造序列化器
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);
        // ④ value 使用该序列化器
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
    /**
     * 库存原子扣减脚本（Day 1 验收资产，decr_stock.lua 仍在 classpath）
     */
    @Bean
    public RedisScript<Long> decrStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/decr_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 整合秒杀执行脚本（幂等 + 扣库存 + 回滚）
     */
    @Bean
    public RedisScript<Long> seckillExecuteScript() {
        DefaultRedisScript <Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill_execute.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 滑动窗口限流脚本
     */
    @Bean
    public RedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/rate_limit.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
