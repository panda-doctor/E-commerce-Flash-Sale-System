package com.ghb.ecommerceflashsalesystem.integration;


import com.ghb.ecommerceflashsalesystem.common.util.RequestIdUtil;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@SpringBootTest
@Transactional  // 事务回滚，防止测试数据污染数据库
public class MapperTest {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    /**
     * Test 1（示例）— 商品查询：验证种子数据正确加载
     *
     * 学习要点：
     * - selectById 是 BaseMapper 提供的基础方法
     * - 用 assertEquals 验证每个字段值与种子数据一致
     * - 注意价格单位是"分"（29900 分 = 299 元）
     */
    @Test
    public void testProductSelectById() {
        // 查询种子数据
        Product product = productMapper.selectById(1L);

        // 验证非空

        assertNotNull(product, "商品不应为 null");

        // 验证基础信息
        assertEquals("秒杀机械键盘", product.getName());
        assertEquals(Long.valueOf(29900L), product.getOriginalPrice(), "原价应为 29900 分");
        assertEquals(Integer.valueOf(1000), product.getTotalStock());
        assertEquals(Integer.valueOf(1000), product.getAvailableStock());

        // 验证状态和时间
        assertEquals(Integer.valueOf(1), product.getStatus(), "默认应上架");
        assertNotNull(product.getCreatedAt());
        assertNotNull(product.getUpdatedAt());

        log.info("商品查询测试通过：id={}, name={}, price={}分", product.getId(), product.getName(), product.getOriginalPrice());
    }

    @Test
    public void testActivitySelectById() {
        SeckillActivity seckillActivity = seckillActivityMapper.selectById(1L);
        assertThat(seckillActivity).isNotNull();
        log.info("查询活动 id=1 结果：{}", seckillActivity);
        assertThat(seckillActivity.getActivityName()).isEqualTo("键盘限时秒杀");
        assertThat(seckillActivity.getSeckillPrice()).isEqualTo(9900L);
        assertThat(seckillActivity.getSeckillStock()).isEqualTo(100);
        assertThat(seckillActivity.getProductId()).isEqualTo(1L);
        assertThat(seckillActivity.getStatus()).isEqualTo(0);        // 未开始
        assertThat(seckillActivity.getPreheatStatus()).isEqualTo(0); // 未预热
        assertThat(seckillActivity.getLimitPerUser()).isEqualTo(1);
        assertThat(seckillActivity.getVersion()).isEqualTo(0);

    }

    @Test
    void testGenerate() {
        String id1 = RequestIdUtil.generate();
        String id2 = RequestIdUtil.generate();
        System.out.println(id1);
        System.out.println(id2);
        // 应看到时间戳相同（同一秒内）且末尾序列递增
    }
}
