package com.ghb.ecommerceflashsalesystem.controller.product;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.PageResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.ProductVO;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.service.product.ProductService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    private final ProductMapper productMapper;

    @GetMapping("/api/products")
    public Result<PageResponse<ProductVO>> listProducts(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword) {
        QueryWrapper<Product> query = new QueryWrapper<Product>().eq("status", ProductStatusEnum.ON_SHELF.getCode());
        if (keyword != null && !keyword.isBlank()) query.like("name", keyword.trim());
        query.orderByDesc("updated_at");
        Page<Product> records = productMapper.selectPage(new Page<>(page, pageSize), query);
        return Result.success(new PageResponse<>(records.getRecords().stream().map(this::toView).toList(),
                records.getCurrent(), records.getSize(), records.getTotal(), records.getPages()));
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

    private ProductVO toView(Product product) {
        ProductVO view = new ProductVO();
        view.setProductId(product.getId());
        view.setName(product.getName());
        view.setDescription(product.getDescription());
        view.setImageUrl(product.getImageUrl());
        view.setOriginalPrice(product.getOriginalPrice());
        view.setTotalStock(product.getTotalStock());
        view.setAvailableStock(product.getAvailableStock());
        view.setStatus(ProductStatusEnum.fromValue(product.getStatus()));
        return view;
    }
}
