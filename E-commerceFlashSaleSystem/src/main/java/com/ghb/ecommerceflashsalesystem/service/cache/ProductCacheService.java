package com.ghb.ecommerceflashsalesystem.service.cache;

import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant.PRODUCT_DETAIL_CACHE_TTL;
import static com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant.PRODUCT_DETAIL_CACHE_TTL_RANDOM_RANGE;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductCacheService {
    private final RedisTemplate<String, Object> redisTemplate;
    /*在当前类中声明一个私有的、不可变的 Redis 操作模板对象，用于执行对 Redis 数据库的所有原子性操作（如存、取、删除等）。
结合你之前的 CacheKeyConstant，它正是用来操作那些缓存键（如 product:detail:123）的核心工具。
    */

    /**
     * 从缓存中获取商品。
     *
     * @param productId 商品编号
     * @return 命中返回 Product，未命中返回 null
     */
    public Product getProductFromCache(Long productId) {
        String key = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + productId;
        Object value = redisTemplate.opsForValue().get(key);
        if(value == null){
            log.info("缓存未命中, key = {}", key);
            return null;
        }
        // 安全类型检查
        if(value instanceof Product){
            log.info("缓存命中, key = {}", key);
            return (Product) value;
        }else{
            // 理论上不会发生，但做防御：删除异常数据
            log.info("缓存数据类型异常，key={}, 实际类型={}，将删除该键", key, value.getClass().getName());
            redisTemplate.delete(key);
            return null;
        }
    }
    /**
     * 将商品写入缓存，随机过期时间 300~360 秒（防缓存雪崩）。
     *
     * @param productId 商品编号
     * @param product   商品对象
     */
    public void setProductToCache(Long productId, Product product) {
        String key = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + productId;
        //基础 300 秒 + 随机 0~60 秒
        long ttl = PRODUCT_DETAIL_CACHE_TTL + ThreadLocalRandom.current().nextInt(PRODUCT_DETAIL_CACHE_TTL_RANDOM_RANGE);
        redisTemplate.opsForValue().set(key, product, ttl, TimeUnit.SECONDS);
        log.info("商品已写入缓存，key={}, ttl={}秒", key, ttl);
    }

    /**
     * 从缓存中删除商品
     *
     * @param productId 商品编号
     */
    public void deleteProductFromCache(Long productId) {
        String key = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + productId;
        redisTemplate.delete(key);
        log.info("商品缓存已删除，key={}", key);
    }


}
