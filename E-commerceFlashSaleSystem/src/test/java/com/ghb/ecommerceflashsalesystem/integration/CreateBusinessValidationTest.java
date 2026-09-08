package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.ActivityRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.ProductRequest;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.ProductCacheService;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.impl.ProductServiceImpl;
import com.ghb.ecommerceflashsalesystem.service.impl.SeckillActivityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * E3 · 创建/更新业务校验（审计整改立项）
 *
 * 覆盖：活动与商品创建的"价格非负 / 库存>0 / 时间序 / productId 存在性 / 每用户限购"校验，
 * 非法入参必须被业务码（PARAM_ERROR / NOT_FOUND）拦截，不得以 FK/脏异常冒 50000。
 *
 * 说明：Service 层手写校验与 Controller @Valid 注解互为双保险。本测试为纯 Mockito 单测，
 * 手动组装 Impl（构造器注入），不启动 Spring 容器，直接验证 Service 层业务码路径。
 */
@ExtendWith(MockitoExtension.class)
public class CreateBusinessValidationTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SeckillActivityMapper seckillActivityMapper;

    @Mock
    private SeckillCacheService seckillCacheService;

    @Mock
    private ProductCacheService productCacheService;

    @Mock
    private RedissonClient redissonClient;

    private ProductServiceImpl productService;
    private SeckillActivityServiceImpl seckillActivityService;

    /** 用例共用的"存在"商品编号（mock 命中） */
    private static final Long PRODUCT_ID = 88001L;

    /** 保证不存在的商品编号，验证活动创建引用不存在商品被拦而非 FK 冒 50000 */
    private static final Long NONEXISTENT_PRODUCT_ID = 888888888888888888L;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productMapper, productCacheService, redissonClient);
        seckillActivityService = new SeckillActivityServiceImpl(seckillActivityMapper, seckillCacheService, productMapper);
    }

    /** 活动"productId 存在性"通过：mock selectById 返回非 null 商品；成功路径 insert 返回 1 */
    private void stubProductExistsAndInsert() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(new Product());
        when(seckillActivityMapper.insert(any(SeckillActivity.class))).thenReturn(1);
    }

    // ---------- 商品创建 ----------

    @Test
    void product_negativePrice_rejected() {
        assertThatThrownBy(() -> productService.saveProduct(buildProductRequest(null, -1L, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    void product_zeroStock_rejected() {
        assertThatThrownBy(() -> productService.saveProduct(buildProductRequest(null, 1000L, 0)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    void product_validCreate_success() {
        when(productMapper.insert(any(Product.class))).thenReturn(1);
        Long id = productService.saveProduct(buildProductRequest(null, 500L, 5));
        assertThat(id).isNotNull();
    }

    // ---------- 活动创建 ----------

    @Test
    void activity_endNotAfterStart_rejected() {
        // 结束时间早于开始时间 → 非法
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        assertThatThrownBy(() -> seckillActivityService.createActivity(
                buildActivityRequest(null, PRODUCT_ID, start, start.minusHours(1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    void activity_negativePrice_rejected() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        ActivityRequest request = buildActivityRequest(null, PRODUCT_ID, start, start.plusHours(1));
        request.setSeckillPrice(-1L);
        assertThatThrownBy(() -> seckillActivityService.createActivity(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    void activity_zeroStock_rejected() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        ActivityRequest request = buildActivityRequest(null, PRODUCT_ID, start, start.plusHours(1));
        request.setSeckillStock(0);
        assertThatThrownBy(() -> seckillActivityService.createActivity(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    void activity_productNotExist_rejectedWithNotFound_notFk50000() {
        // 显式 stub 该商品不存在（selectById 返回 null），验证被 NOT_FOUND 拦截而非 FK 冒 50000
        when(productMapper.selectById(NONEXISTENT_PRODUCT_ID)).thenReturn(null);
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        assertThatThrownBy(() -> seckillActivityService.createActivity(
                buildActivityRequest(null, NONEXISTENT_PRODUCT_ID, start, start.plusHours(1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    void activity_limitPerUserNotOne_rejected() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        assertThatThrownBy(() -> seckillActivityService.createActivity(
                buildActivityRequest(null, PRODUCT_ID, start, start.plusHours(1), 2)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    void activity_validCreate_success() {
        stubProductExistsAndInsert();
        Long id = seckillActivityService.createActivity(
                buildActivityRequest(null, PRODUCT_ID,
                        LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2)));
        assertThat(id).isNotNull();
    }

    // ---------- 活动更新（复用同一校验入口） ----------

    @Test
    void activityUpdate_badTime_rejected() {
        // activityId 有值=更新：非法时间窗（结束早于开始）同样在入口被业务码拦截，而非落到 DB 更新
        LocalDateTime start = LocalDateTime.now().plusHours(3);
        assertThatThrownBy(() -> seckillActivityService.createActivity(
                buildActivityRequest(12345L, PRODUCT_ID, start, start.minusHours(1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    // ---------- helpers ----------

    private ProductRequest buildProductRequest(Long productId, long originalPrice, int totalStock) {
        ProductRequest request = new ProductRequest();
        request.setProductId(productId);
        request.setName("校验测试商品");
        request.setDescription("E3 校验测试");
        request.setOriginalPrice(originalPrice);
        request.setTotalStock(totalStock);
        request.setStatus(ProductStatusEnum.ON_SHELF);
        return request;
    }

    private ActivityRequest buildActivityRequest(Long activityId, Long productId,
                                                 LocalDateTime startTime, LocalDateTime endTime) {
        return buildActivityRequest(activityId, productId, startTime, endTime, 1);
    }

    private ActivityRequest buildActivityRequest(Long activityId, Long productId,
                                                 LocalDateTime startTime, LocalDateTime endTime,
                                                 int limitPerUser) {
        ActivityRequest request = new ActivityRequest();
        request.setActivityId(activityId);
        request.setProductId(productId);
        request.setActivityName("E3 校验测试活动");
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setSeckillPrice(9900L);
        request.setSeckillStock(10);
        request.setLimitPerUser(limitPerUser);
        return request;
    }
}
