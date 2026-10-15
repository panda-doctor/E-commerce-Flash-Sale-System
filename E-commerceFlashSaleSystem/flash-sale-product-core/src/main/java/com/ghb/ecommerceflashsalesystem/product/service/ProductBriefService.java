package com.ghb.ecommerceflashsalesystem.product.service;

import com.ghb.ecommerceflashsalesystem.contracts.internal.ProductBriefView;
import com.ghb.ecommerceflashsalesystem.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品摘要查询服务（P3 Day 5）：给"活动广场列表"这类跨域调用提供<b>批量摘要</b>。
 *
 * <p>为什么单列一个服务：`/internal/**` 属于服务间接口，控制器不得直连 mapper（架构规则 R4），
 * 而 {@code ProductService} 的既有方法都是"详情/分页"语义，不适合承载"按 id 批量取摘要"。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductBriefService {

    private final ProductMapper productMapper;

    /**
     * 按 id 批量取商品摘要。
     *
     * @param productIds 商品编号集合（可为 null / 空）
     * @return 摘要列表；查不到的 id 不会出现在结果里（调用方自行处理缺失）
     */
    public List<ProductBriefView> findBriefs(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = productIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return productMapper.selectBatchIds(distinctIds).stream()
                .map(p -> new ProductBriefView(p.getId(), p.getName(), p.getImageUrl(), p.getOriginalPrice()))
                .toList();
    }
}
