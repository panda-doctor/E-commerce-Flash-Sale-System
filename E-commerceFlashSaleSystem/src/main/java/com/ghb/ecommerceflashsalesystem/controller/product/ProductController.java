package com.ghb.ecommerceflashsalesystem.controller.product;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.vo.ProductVO;
import com.ghb.ecommerceflashsalesystem.service.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品查询控制器
 */
@RestController
@RequiredArgsConstructor //自动为类中所有“必须初始化”的字段（即 final 字段或标记了 @NonNull 的字段）生成一个有参构造器
public class ProductController {
    private final ProductService productService;
    /**
     * 查询商品详情
     *
     * @param productId 商品编号
     * @return 商品详情
     */
    @GetMapping("/api/products/{productId}")
    public Result<ProductVO> getProductById(@PathVariable Long productId) {
        ProductVO productVO = productService.getProductDetail(productId);
        if (productVO == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，productId=" + productId);
        }
        return Result.success(productVO);
    }
}
