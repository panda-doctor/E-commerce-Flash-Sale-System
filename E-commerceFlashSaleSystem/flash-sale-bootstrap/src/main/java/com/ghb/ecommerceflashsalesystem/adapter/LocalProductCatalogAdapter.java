package com.ghb.ecommerceflashsalesystem.adapter;

import com.ghb.ecommerceflashsalesystem.activity.port.ProductCatalogPort;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link ProductCatalogPort} 的<b>本地实现</b>（单体专用）：进程内直读 product 表。
 *
 * <p>为什么单体可以直读：单体本身就是"尚未拆走的模块的家"，product 表对它仍是本地表；
 * 而 activity <b>应用</b>已经拆出去，必须走 product 服务的接口（{@code HttpProductCatalogAdapter}）。
 * 同一端口、两种装配 —— 这正是端口与适配器模式要表达的东西。</p>
 */
@Component
@RequiredArgsConstructor
public class LocalProductCatalogAdapter implements ProductCatalogPort {

    private final ProductMapper productMapper;

    @Override
    public boolean exists(Long productId) {
        return productId != null && productMapper.selectById(productId) != null;
    }

    @Override
    public Map<Long, ProductBrief> findBriefs(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId,
                        p -> new ProductBrief(p.getId(), p.getName(), p.getImageUrl(), p.getOriginalPrice())));
    }
}
