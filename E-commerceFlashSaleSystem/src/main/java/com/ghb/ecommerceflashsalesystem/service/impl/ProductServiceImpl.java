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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品服务实现，包含缓存旁路逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;
    private final ProductCacheService productCacheService;

    @Override
    public ProductVO getProductDetail(Long productId){
        // 1.先查缓存
        Product cacheProduct = productCacheService.getProductFromCache(productId);
        if (cacheProduct != null){
            log.info("商品详情命中缓存，productId={}", productId);
            return convertToVO(cacheProduct, true);
        }
        // 2. 缓存未命中，查数据库
        log.info("商品详情缓存未命中，查询数据库，productId={}", productId);
        Product product = productMapper.selectById(productId);
        if (product == null){
            log.info("商品不存在，productId={}", productId);
            return null;
        }

        // 3.会写缓存
        productCacheService.setProductToCache(productId, product);

        //4. 转VO回写
        return convertToVO(product, false);

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
            productMapper.updateById(product);
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


}
