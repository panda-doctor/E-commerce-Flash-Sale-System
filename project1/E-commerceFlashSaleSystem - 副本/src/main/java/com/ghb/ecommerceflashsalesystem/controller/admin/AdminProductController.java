package com.ghb.ecommerceflashsalesystem.controller.admin;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.ProductRequest;
import com.ghb.ecommerceflashsalesystem.service.cache.ProductCacheService;
import com.ghb.ecommerceflashsalesystem.service.product.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端商品控制器
 */
@RestController
@RequiredArgsConstructor
public class AdminProductController {
    private final ProductService productService;
    private final ProductCacheService productCacheService;

    /**
     * 创建或更新商品
     */
    @PostMapping("/api/admin/products")
    public Result<Map<String, Object>> saveProducts(@Valid  @RequestBody ProductRequest request) {
        // 保存商品
        Long productId = productService.saveProduct(request);

        //删除缓存(不管创建还是更新，都要清缓存)
        productCacheService.deleteProductFromCache(productId);

        // 返回 productId：雪花 ID 可能超过 JS 安全整数（2^53），统一按字符串返回，前端字符串透传不丢精度
        Map<String, Object> data = new HashMap<>();
        data.put("productId", String.valueOf(productId));
        return Result.success(data);
    }
}
