package com.ghb.ecommerceflashsalesystem.product.controller;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.common.api.PageResponse;
import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import com.ghb.ecommerceflashsalesystem.product.vo.ProductVO;
import com.ghb.ecommerceflashsalesystem.product.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品查询控制器
 */
@RestController
@RequiredArgsConstructor //自动为类中所有“必须初始化”的字段（即 final 字段或标记了 @NonNull 的字段）生成一个有参构造器
@Validated
public class ProductController {
    private final ProductService productService;

    @GetMapping("/api/products")
    public Result<PageResponse<ProductVO>> listProducts(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword) {
        // P0 Day 4：分页查询收敛到服务层（分层规则 R4：controller 不直接依赖 mapper）
        return Result.success(productService.listOnShelf(page, pageSize, keyword));
    }
    /**
     * 查询商品详情
     *
     * @param productId 商品编号
     * @return 商品详情
     */
    @GetMapping("/api/products/{productId}")
    public Result<ProductVO> getProductById(@PathVariable Long productId) {
        ProductVO productVO = productService.getProductDetail(productId);
        if (productVO == null || productVO.getStatus() != ProductStatusEnum.ON_SHELF) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，productId=" + productId);
        }
        return Result.success(productVO);
    }

}
