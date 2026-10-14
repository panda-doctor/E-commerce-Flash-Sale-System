package com.ghb.ecommerceflashsalesystem.product.service;

import com.ghb.ecommerceflashsalesystem.common.api.PageResponse;
import com.ghb.ecommerceflashsalesystem.product.dto.request.ProductRequest;
import com.ghb.ecommerceflashsalesystem.product.vo.ProductVO;

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
     * 分页查询在售商品（keyword 为空时返回全部在售，按更新时间倒序）。
     *
     * <p>P0 Day 4：原先由控制器直连 Mapper 组装分页，收敛到服务层（分层规则 R4）。</p>
     */
    PageResponse<ProductVO> listOnShelf(long page, long pageSize, String keyword);

    /**
     * 保存商品（创建或更新）
     *
     * @param request 商品请求 DTO
     * @return 商品编号
     */
    Long saveProduct(ProductRequest request);
}
