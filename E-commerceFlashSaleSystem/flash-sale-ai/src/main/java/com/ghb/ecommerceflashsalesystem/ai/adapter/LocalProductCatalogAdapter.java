package com.ghb.ecommerceflashsalesystem.ai.adapter;

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
 * {@link ProductCatalogPort} 的 <b>ai 侧本地实现</b>（P3 Day 5）。
 *
 * <p>ai 服务目前仍以"直读 DB"做商品/活动目录（见 plan.md 的 P2 Day 3 记录：ai 无库，
 * 过渡期直连 mapper）。本类把这份直读收敛到端口后面：形状统一了，将来 ai 改为调用
 * product 服务接口时，只需替换这一个类。</p>
 *
 * <p>单体侧的同名实现是 {@code bootstrap.adapter.LocalProductCatalogAdapter}，
 * activity 服务侧则是 HTTP 适配器 —— 同一端口、三种装配。</p>
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
