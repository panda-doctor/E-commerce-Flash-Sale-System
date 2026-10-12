package com.ghb.ecommerceflashsalesystem.service.product;

import com.ghb.ecommerceflashsalesystem.domain.dto.request.ProductRequest;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.vo.ProductVO;

/**
 * 商品服务接口
 */
public interface ProductService {
/**
 * 获取商品详情（含缓存旁路）
 *
 * @param productId 商品编号
 * @return 商品视图对象，不存在返回 null
 */
    ProductVO getProductDetail(Long productId);
    /**
     * 保存商品（创建或更新）
     *
     * @param request 商品请求 DTO
     * @return 商品编号
     */
    Long saveProduct(ProductRequest request);
}
