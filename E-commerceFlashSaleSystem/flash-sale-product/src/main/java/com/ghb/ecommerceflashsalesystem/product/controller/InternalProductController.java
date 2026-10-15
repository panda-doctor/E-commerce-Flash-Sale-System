package com.ghb.ecommerceflashsalesystem.product.controller;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.contracts.internal.ProductBriefView;
import com.ghb.ecommerceflashsalesystem.contracts.internal.ProductBriefsRequest;
import com.ghb.ecommerceflashsalesystem.product.service.ProductBriefService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部接口：批量商品摘要（P3 Day 5）。
 *
 * <p>仅供服务间调用（如 activity 的"活动广场列表"补商品名/图/原价），<b>不经网关</b>：
 * 访问控制由 infra 的 {@code InternalAccessWebConfig} 负责（{@code X-Internal-Token}，
 * 未配置令牌则一律拒绝）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductBriefService productBriefService;

    /**
     * POST /internal/products/briefs
     *
     * @param request 商品 id 集合
     * @return 命中的商品摘要列表
     */
    @PostMapping("/briefs")
    public Result<List<ProductBriefView>> briefs(@RequestBody ProductBriefsRequest request) {
        List<ProductBriefView> briefs = productBriefService.findBriefs(request.productIds());
        log.debug("内部批量商品摘要查询，请求 {} 个，命中 {} 个", 
                request.productIds() == null ? 0 : request.productIds().size(), briefs.size());
        return Result.success(briefs);
    }
}
