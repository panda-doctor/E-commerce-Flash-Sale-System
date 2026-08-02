package com.ghb.ecommerceflashsalesystem.service.impl;

import com.ghb.ecommerceflashsalesystem.common.util.IdGenerator;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.ProductRequest;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.ProductVO;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.ProductCacheService;
import com.ghb.ecommerceflashsalesystem.service.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;

import java.util.concurrent.TimeUnit;

import static com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant.PRODUCT_LOCK_PREFIX;

/**
 * 商品服务实现，包含缓存旁路逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;
    private final ProductCacheService productCacheService;
    private final RedissonClient redissonClient;

    //锁参数
    private static final long LOCK_WAIT_TIME = 2;      // 最多等待2秒
    private static final long LOCK_LEASE_TIME = 30;     // 锁持有30秒自动释放

    @Override
    public ProductVO getProductDetail(Long productId){
        // 1.先查缓存
        Product cacheProduct = productCacheService.getProductFromCache(productId);
        if (cacheProduct != null){
            log.info("商品详情命中缓存，productId={}", productId);
            return convertToVO(cacheProduct, true);
        }
/*        // 2. 缓存未命中，查数据库
        log.info("商品详情缓存未命中，查询数据库，productId={}", productId);
        Product product = productMapper.selectById(productId);
        if (product == null){
            log.info("商品不存在，productId={}", productId);
            return null;
        }*/


        //关键逻辑校验
        //空值标记不被误删：getProductFromCache 中，空值标记走 else if 返回 null，不触发删除。
        //
        //穿透保护生效：商品不存在时，60 秒内再次查询直接返回 null，不走数据库。
        //
        //缓存一致性：管理员更新商品时，deleteProductFromCache 会同时删除真实缓存和空值标记，确保后续查询正确。

        // 2. 检查是否命中空值标记（缓存穿透保护）
        if (productCacheService.isProductEmptyFromCache(productId)){
            log.info("命中空值缓存，productId={}", productId);
            return null;
        }
/*
        // 3. 真未命中，查询数据库
        log.info("商品详情缓存未命中，查询数据库，productId={}", productId);
        Product product = productMapper.selectById(productId);
        if (product == null){
            //写入空值标记，防止缓存穿透
            productCacheService.setProductEmptyToCache(productId);
            log.info("商品不存在，已写入空值缓存，productId={}", productId);
            return null;
        }*/

        // 3. 真未命中，尝试获取互斥锁，防止缓存击穿
        String lockKey = PRODUCT_LOCK_PREFIX + productId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            // 尝试获取锁，等待2秒，持有30秒
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (locked) {
                // 成功获取锁，进行双重检查
                log.info("获取锁成功，productId={}", productId);

                // 双重检查：再次查缓存（可能在等待期间已被其他线程填充）
                Product doubleCheckProduct = productCacheService.getProductFromCache(productId);
                if (doubleCheckProduct != null) {
                    log.info("双重检查命中缓存，productId={}", productId);
                    return convertToVO(doubleCheckProduct, true);
                }

                // 双重检查空值标记
                if (productCacheService.isProductEmptyFromCache(productId)) {
                    log.info("双重检查命中空值缓存，productId={}", productId);
                    return null;
                }

                // 仍然未命中，查询数据库
                log.info("缓存未命中，查询数据库，productId={}", productId);
                Product product = productMapper.selectById(productId);
                if (product == null) {
                    //写入空值标记，防止缓存穿透
                    productCacheService.setProductEmptyToCache(productId);
                    log.info("商品不存在，已写入空值缓存，productId={}", productId);
                    return null;
                }
                // 4.回写缓存
                productCacheService.setProductToCache(productId, product);

                //5. 转VO回写
                return convertToVO(product, false);

            }else{
                // 获取锁超时（等待2秒未拿到锁），直接查库兜底，保证可用性
                log.warn("获取锁超时，直接查库，productId={}", productId);
                Product product = productMapper.selectById(productId);
                if (product == null) {
                    // 注意：这里不写空值缓存，因为其他线程可能正在写入，避免重复写
                    return null;
                }
                // 也不回写缓存，由获取锁的线程负责写缓存
                return convertToVO(product, false);
            }
        }catch (InterruptedException e){
            // 中断异常，恢复中断状态并返回null或降级
            Thread.currentThread().interrupt();
            log.error("获取锁被中断，直接查库兜底，productId={}", productId, e);
            Product product = productMapper.selectById(productId);
            if (product == null) {
                return null;
            }
            return convertToVO(product, false);
        }finally {
            // 释放锁：只有当前线程持有锁时才释放
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("锁已释放，productId={}", productId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProduct(ProductRequest request) {
        Product product = new Product();
        // 复制共同字段
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setTotalStock(request.getTotalStock());
        product.setAvailableStock(request.getTotalStock()); // 默认可用库存=总库存
        product.setStatus(request.getStatus().getCode());   // 枚举转 Integer

        Long productId = request.getProductId();
        if (productId == null){
            // 创建生成ID
            product.setId(IdGenerator.nextId());
            productMapper.insert(product);
            log.info("创建商品成功，productId={}", product.getId());
            return product.getId();
        }else{
            // 更新：设置 ID，执行更新
            product.setId(productId);
            // 注意：updateById 只更新非 null 字段，但我们希望全部覆盖，可改用 update 全部字段
            // 这里使用 updateById，但需要确保所有字段都设置了，如果某些字段为空不想覆盖，可选择性使用
            // 我们这里使用 updateById，它会更新所有非 null 字段，但 product 对象里所有字段都设置了，所以没问题
            int rows = productMapper.updateById(product);
            if (rows == 0) {
                log.warn("商品不存在，无法更新，productId={}", productId);
                throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，无法更新，productId=" + productId);
            }
            log.info("更新商品成功，productId={}", productId);
            return productId;
        }
    }

    /**
     * 实体转 VO
     *
     * @param product   商品实体
     * @param cacheHit 是否缓存命中
     * @return 商品视图对象
     */
    private ProductVO convertToVO(Product product, boolean cacheHit) {
        ProductVO productVO = new ProductVO();
        productVO.setProductId(product.getId());
        productVO.setName(product.getName());
        productVO.setDescription(product.getDescription());
        productVO.setImageUrl(product.getImageUrl());
        productVO.setOriginalPrice(product.getOriginalPrice());
        productVO.setTotalStock(product.getTotalStock());
        productVO.setAvailableStock(product.getAvailableStock());

        // 枚举转换：处理 null 情况
        Integer statusCode = product.getStatus();
        if (statusCode != null){
            ProductStatusEnum productStatusEnum = ProductStatusEnum.fromValue(statusCode);
            productVO.setStatus(productStatusEnum);
        }
        productVO.setCacheHit(cacheHit);
        return productVO;
    }
//这段 convertToVO 方法是一个典型的 实体对象（Entity）转视图对象（VO） 的转换器，主要应用在 Service 层 返回数据给 Controller（前端）之前的最后一环。
//
//它的核心作用和功能可以拆解为以下三点：
//
//1. 核心数据映射（字段复制）
//将 Product 实体中的核心数据字段（ID、名称、描述、价格、库存等）逐一生成为 ProductVO 对象。
//
//注意：这里的 Product 的 id 映射给了 ProductVO 的 productId，表明 VO 层可能对前端暴露的字段名进行了规范化（如明确语义），或者 Product 实体的主键就叫 id 而 VO 层要求叫 productId。
//
//2. 业务状态码的“语义化”转换（重要）
//这是这段代码最关键的逻辑：
//
//数据库或实体中存储的 status 是一个 Integer 数字（例如 0、1、2）。
//
//前端通常不希望看到冰冷的数字，也不希望前端去猜 1 代表什么。
//
//通过 ProductStatusEnum.fromValue(statusCode)，将数字转换成对应的 Java 枚举对象（包含状态名称、描述等信息）。
//
//安全处理：加上了 if (statusCode != null) 判空，防止数据库字段为空时触发空指针异常（NPE）。
//
//3. 传递“非业务”的元数据（缓存打标）
//参数中有一个特殊的 boolean cacheHit（是否命中缓存）。
//
//这不是 Product 实体本身的属性，而是服务层在执行查询时的状态。
//
//将其塞入 VO 并返回给前端，主要有两个用处：
//
//调试/监控：前端开发或测试人员在浏览器 Network 面板中看到 cacheHit: true，可以直观确认当前数据是来自 Redis（缓存）还是 MySQL（数据库）。
//
//灰度/AB测试：某些场景下，前端可以根据是否命中缓存来决定是否展示“新鲜度”提示（虽然通常不这么干，但调试时极其实用）。
//
//这段代码体现的设计规范
//它将 “数据存储层”（Entity）和 “数据展示层”（VO）彻底隔离。即使 Product 实体里有 createTime、updateTime、逻辑删除标记等敏感或冗余字段，也不会被泄露给前端，有效防止了接口的过度暴露。

}
