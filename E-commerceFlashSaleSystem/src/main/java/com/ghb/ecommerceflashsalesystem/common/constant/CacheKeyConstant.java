package com.ghb.ecommerceflashsalesystem.common.constant;
//这段代码是典型的常量管理工具类，为分布式缓存系统的键命名提供规范、统一、可维护的基础设施，其核心目的是集中管理应用中使用的 Redis 或其他缓存系统的键前缀,是项目工程化实践中的常见模式。
/**
 * 缓存键常量
 */
public class CacheKeyConstant {

    private CacheKeyConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
    /**
     * 商品详情缓存前缀
     */
    public static final String PRODUCT_DETAIL_PREFIX = "product:detail:";

    /**
     * 商品详情缓存基础过期时间（秒），300秒 = 5分钟
     */
    public static final long PRODUCT_DETAIL_CACHE_TTL = 300L;

    /**
     * 商品详情缓存随机偏移范围（秒），0~60秒
     * 用于防缓存雪崩，实际过期时间为 TTL + random(0, TTL_RANDOM_RANGE)
     */
    public static final int PRODUCT_DETAIL_CACHE_TTL_RANDOM_RANGE = 60;

    /**
     * 商品详情空值缓存过期时间（秒），60秒
     */
    public static final long PRODUCT_DETAIL_CACHE_EMPTY_TTL = 60L;

    /**
     * 商品缓存互斥锁前缀
     */
    public static final String PRODUCT_LOCK_PREFIX = "product:lock:";
}
/*
 * 3. 实际用途与意义
 * 避免硬编码：将分散在代码各处的字符串字面量（如 "product:detail:"）统一提取到常量类中，修改时只需改一处，降低维护成本。

 * 提高可读性：通过有意义的常量名（PRODUCT_DETAIL_PREFIX）明确表达其业务含义。

 * 规范缓存键命名：统一前缀便于按业务模块组织缓存（例如在 Redis 中可按前缀扫描、监控或批量失效）。

 * 与缓存策略协同：实际使用时，会拼接该前缀与具体商品 ID（如 product:detail:123），形成完整的缓存键。
 */


