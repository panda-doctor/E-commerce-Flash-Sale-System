package com.ghb.ecommerceflashsalesystem.integration;


import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.vo.ProductVO;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.service.product.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest
public class ProductCacheProtectionTest {
    @Autowired
    /*@Autowired（Spring 原生注解）
作用：告诉 Spring 容器，我需要一个这种类型的对象，你帮我找一下并“注入”进来。

注入的是什么：真实的、已经初始化好的 Bean 实例。

在你的代码中：

@Autowired private ProductService productService;
→ 注入真实的 ProductService 业务逻辑类（里面真的会去调用 Mapper 查数据库）。

@Autowired private RedisTemplate<String, Object> redisTemplate;
→ 注入真实的 Redis 操作模板（真的会去连接 Redis 服务器执行读写操作）。

*/
    private ProductService productService;

    @MockBean
    /*@MockBean（Spring Boot Test 测试专用注解）
作用：专门用在测试类中。它会创建一个模拟（Mock）对象，并用这个假对象替换掉 Spring 容器中原本的真实对象。

注入的是什么：一个假的对象（代理对象），它不会执行任何真正的逻辑（如数据库查询），而是返回你预先设定好的“假数据”。

在你的代码中：

@MockBean private ProductMapper productMapper;
→ 真实的 ProductMapper 会被覆盖掉。当 ProductService 内部调用 productMapper.selectById() 时，并不会真正去查数据库，而是会走 Mock 的逻辑（比如你设定了 when(...).thenReturn(...)，它就返回你指定的假对象）。*/
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long NON_EXISTENT_PRODUCT_ID = 999999L;
    private static final Long EXISTENT_PRODUCT_ID = 1L;
@BeforeEach
/*@BeforeEach 的作用是：在每一个测试方法执行之前，都自动运行一次被它标记的方法。

它的唯一目的就是：重置环境、准备数据，确保每个测试方法都在一个“干净、可控”的起点上运行，互相不干扰。*/
    void setUp() {
        // 清理测试相关的所有缓存键（真实商品、空值标记、锁）
        String detailKey = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + "*";
        String lockKey = CacheKeyConstant.PRODUCT_LOCK_PREFIX + "*";
        redisTemplate.delete(redisTemplate.keys(detailKey));
        redisTemplate.delete(redisTemplate.keys(lockKey));
        log.info("测试前已清理缓存键");
    }

    /**
     * 测试1：穿透防护 —— 连续查询不存在的商品，只查询一次数据库
     */
    @Test
    void testCachePenetration()throws Exception{
        // Mock Mapper：查询不存在的商品返回 null
        when(productMapper.selectById(eq(NON_EXISTENT_PRODUCT_ID))).thenReturn(null);

        // 第一次查询
        ProductVO result1 = productService.getProductDetail(NON_EXISTENT_PRODUCT_ID);
        assertThat(result1).isNull();

        // 第二次查询
        ProductVO result2 = productService.getProductDetail(NON_EXISTENT_PRODUCT_ID);
        assertThat(result2).isNull();

        // 验证数据库只被查询了一次
        verify(productMapper, times(1)).selectById(NON_EXISTENT_PRODUCT_ID);

        // 断言 Redis 中存在空值标记
        String emptyKey = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + NON_EXISTENT_PRODUCT_ID;
        Object cachedValue = redisTemplate.opsForValue().get(emptyKey);
        assertThat(cachedValue).isEqualTo(""); // 空字符串即为空值标记
        log.info("穿透测试通过：空值标记已存在，数据库仅查询1次");

    }
/**
 * 测试2：击穿防护 —— 8线程并发查询同一个存在的商品，只查询一次数据库
 */
    @Test
    void testCacheBreakdown() throws Exception {
        // 构造完整的商品对象（避免 convertToVO 时 NPE）
        Product mockProduct = new Product();
        mockProduct.setId(EXISTENT_PRODUCT_ID);
        mockProduct.setName("测试商品");
        mockProduct.setDescription("用于击穿测试");
        mockProduct.setImageUrl("http://test.jpg");
        mockProduct.setOriginalPrice(10000L);
        mockProduct.setTotalStock(100);
        mockProduct.setAvailableStock(100);
        mockProduct.setStatus(1); // ON_SHELF 对应 1

        // Mock Mapper：查询存在商品，模拟慢查询（300ms），拉开并发窗口
        when(productMapper.selectById(eq(EXISTENT_PRODUCT_ID))).thenAnswer(invocation -> {
            Thread.sleep(300); // 模拟慢查询
            return mockProduct;
        });

        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 收集各线程返回结果，用于最终验证（非必须）
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 等待所有线程同时启动
                    startLatch.await();
                    // 执行查询
                    ProductVO vo = productService.getProductDetail(EXISTENT_PRODUCT_ID);
                    // 简单断言：商品应该存在
                    assertThat(vo).isNotNull();
                    assertThat(vo.getProductId()).isEqualTo(EXISTENT_PRODUCT_ID);
                } catch (Exception e) {
                    exceptionRef.set(e);
                    log.error("线程执行异常", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 触发所有线程同时执行
        startLatch.countDown();

        // 等待所有线程完成（最多10秒）
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 检查是否有异常
        if (exceptionRef.get() != null) {
            throw new RuntimeException("并发测试中出现异常", exceptionRef.get());
        }

        // 关闭线程池
        executor.shutdown();

        // 验证数据库只被查询了一次
        verify(productMapper, times(1)).selectById(EXISTENT_PRODUCT_ID);

        // 验证锁已释放（锁键不存在）
        String lockKey = CacheKeyConstant.PRODUCT_LOCK_PREFIX + EXISTENT_PRODUCT_ID;
        Boolean lockExists = redisTemplate.hasKey(lockKey);
        assertThat(lockExists).isFalse();

        // 验证缓存中已有真实商品
        String detailKey = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + EXISTENT_PRODUCT_ID;
        Object cachedProduct = redisTemplate.opsForValue().get(detailKey);
        assertThat(cachedProduct).isInstanceOf(Product.class);
        Product cached = (Product) cachedProduct;
        assertThat(cached.getId()).isEqualTo(EXISTENT_PRODUCT_ID);

        log.info("击穿测试通过：8线程并发仅查询数据库1次，锁已释放");
    }
}
