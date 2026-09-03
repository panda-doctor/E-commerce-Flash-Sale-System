package com.ghb.ecommerceflashsalesystem.integration;


import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.common.util.IdGenerator;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.ActivityCheckResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.ProductVO;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.product.ProductService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillActivityService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
public class Phase1IntegrationTest {
    @Autowired
    /**
     * 含义：告诉 Spring 容器，我需要一个真实的 Bean，请你从容器里找一个匹配的实例注入给我。
     *
     * 默认行为：按类型查找。如果找到多个同类型实例，会按属性名或 @Qualifier 进一步匹配。
     *
     * 测试中的表现：在测试环境下，它会加载完整的 Spring 上下文，把真实的类、真实的数据库连接、真实的网络客户端（如果有配置）全部初始化好。调用它的方法，会执行真正的业务逻辑。
     */
    private ProductService productService;

    @Autowired
    private SeckillActivityService seckillActivityService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @MockBean
    /**
     *
     *  含义：告诉 Spring 容器，我要创建一个 Mock（模拟对象），并用这个模拟对象替换掉容器里现有的真实 Bean。
     *
     *  底层原理：基于 Mockito 框架。它生成一个经过 CGLIB 代理的假对象，这个对象的所有方法默认返回空值（null、0 或 false）。
     *
     *  测试中的表现：它不会执行任何真正的代码。你需要通过 when(...).thenReturn(...) 来手动“训练”它，告诉它在特定输入下返回什么结果。它主要用于隔离外部依赖。
     */
    private ProductMapper productMapper;

    @MockBean
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 穿透测试专用：数据库里不存在的商品 ID，用于验证空值缓存（7.3）
     */
    private static final Long NON_EXISTENT_PRODUCT_ID = 999999L;

    /**
     * 击穿测试专用：并发热点商品 ID，数据由 mock 提供、不真实打库（7.4）
     */
    private static final Long EXISTENT_PRODUCT_ID = 888888L;

    private Long testProductId;
    private Long testActivityId;

    @BeforeEach
    /**
     * @BeforeEach 用来标记一个方法，保证在每个 @Test 测试方法执行之前，都会自动运行一次这个方法。
     */
    void setUp() {
        // 生成随机的ID避免冲突
        testProductId = IdGenerator.nextId();
        testActivityId = IdGenerator.nextId();

        // 清理所有测试相关的Redis键
        cleanRedisKeys(CacheKeyConstant.PRODUCT_DETAIL_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.PRODUCT_LOCK_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + "*");

        log.info("测试初始化完成，productId={}, activityId={}", testProductId, testActivityId);
    }
    @AfterEach
    /**
     * 释放外部资源：比如关闭临时文件流、断开测试专用的 Socket 连接。
     *
     * 清除脏数据：清空内存缓存、集合或 ThreadLocal 变量。
     *
     * 输出测试后置日志：方便在 CI/CD 流水线中追踪每个用例的结束状态。
     *
     * （关键）做最终的后置断言：比如确保每次测试结束后，Mock 的依赖没有被意外调用。
     */
    void tearDown() {
        // 测试后清理，避免影响其他测试
        cleanRedisKeys(CacheKeyConstant.PRODUCT_DETAIL_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.PRODUCT_LOCK_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + "*");
        cleanRedisKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + "*");
        log.info("测试后清理Redis键完成");
    }

    //private 意味着它通常只在当前测试类内部被 @AfterEach 调用，不对外暴露。
    private void cleanRedisKeys(String pattern) {
        //调用 Redis 的 KEYS 命令。它会全量扫描 Redis 的键空间，把所有匹配 pattern 的键名以 Set 集合的形式一次性拉取到应用程序的内存（JVM 堆内存）中。
        //注意：redisTemplate.keys() 底层直接对应 Redis 的 KEYS 指令，执行期间会阻塞 Redis 单线程。
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            //isEmpty() 是 Java Collection（集合）接口提供的标准方法。
            //底层实现：它不是去数集合里有多少个元素，而是直接判断集合内部的 size 成员变量是否等于 0。这个操作的时间复杂度是 O(1)（极快，仅仅是比较一个整数）。
            //!keys.isEmpty() 意味着：“只要集合里至少有一个元素，就进入 if 代码块。”
            redisTemplate.delete(keys);
            log.debug("清理Redis键: {}", keys);
        }
    }

    // ======================== 测试用例 ========================

    /**
     * 用例1：预热成功，验证缓存写入正确
     */
    @Test
    void testPreheatSuccess() {
        // 准备：创建商品
        prepareProductMock();
        //创建活动

        SeckillActivity activity = prepareActivityMock(false, LocalDateTime.now().plusHours(2));
        //预热
        seckillCacheService.preheatActivity(testActivityId);

        //Redis中存在的Hash和String
        String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + testActivityId;
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + testActivityId;

        //API 断言（Assertion）是在 API 测试中，用于验证接口响应数据是否符合预期的自动化检查机制
        assertThat(redisTemplate.hasKey(activityKey)).isTrue();
        assertThat(redisTemplate.hasKey(stockKey)).isTrue();

        // 验证Hash字段
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(activityKey);
        assertThat(hash.get("activityId")).isEqualTo(testActivityId);
        assertThat(hash.get("productId")).isEqualTo(testProductId);
        assertThat(hash.get("activityName")).isEqualTo("测试活动");
        assertThat(hash.get("seckillPrice")).isEqualTo(9900);
        assertThat(hash.get("seckillStock")).isEqualTo(100);
        assertThat(hash.get("limitPerUser")).isEqualTo(1);
        assertThat(hash.get("status")).isEqualTo(0); // NOT_STARTED

        // 验证库存String
        Object stock = redisTemplate.opsForValue().get(stockKey);
        assertThat(stock).isEqualTo(100);

        //验证TTL存在（大于0）
        Long ttl = redisTemplate.getExpire(activityKey);
        assertThat(ttl).isGreaterThan(0);

        // 验证数据库预热状态已更新（通过mock验证）
        verify(seckillActivityMapper, times(1)).update(any(), any());
    }

    /**
     * 用例2：防重预热，第二次预热抛异常
     */
    @Test
    void testPreheatDuplicate() {
        // 第一次预热成功 （先mock第一次行为）
        prepareProductMock();
        SeckillActivity activity = prepareActivityMock(false, LocalDateTime.now().plusHours(2));
        seckillCacheService.preheatActivity(testActivityId);

        // 第二次预热：mock selectById返回已预热的活动
        when(seckillActivityMapper.selectById(testActivityId)).thenReturn(activity);
        //设置 preheat_status=1
        activity.setPreheatStatus(1);

        //执行第二次预热， 应抛出BusinessException
        assertThatThrownBy(() -> seckillCacheService.preheatActivity(testActivityId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动预热成功！！！不要重复，傻鸟！！！");

        //  验证update仅调用一次（第一次预热时更新，第二次不会更新）
        verify(seckillActivityMapper, times(1)).update(any(), any());
    }

    /**
     * 用例3：预热后查询详情，命中缓存不查库
     */
    @Test
    void testCacheHitAfterPreheat() {
        prepareProductMock();
        SeckillActivity activity = prepareActivityMock(false, LocalDateTime.now().plusHours(2));
        seckillCacheService.preheatActivity(testActivityId);

        //重置Mock调用记录
        reset(seckillActivityMapper);

        //查询详情
        SeckillActivityVO vo = seckillActivityService.getActivityDetail(testActivityId);

        assertThat(vo).isNotNull();
        assertThat(vo.getActivityId()).isEqualTo(testActivityId);
        assertThat(vo.getStock()).isEqualTo(100); //来自缓存

        //验证未查库
        verify(seckillActivityMapper, times(0)).selectById(testActivityId);

    }

    /**
     * 用例4：未预热活动查询详情，走DB回源
     */
    @Test
    void testDbFallbackWithoutPreheat() {
        prepareProductMock();
        SeckillActivity activity = prepareActivityMock(false, LocalDateTime.now().plusHours(2));

        //不预热
        SeckillActivityVO vo = seckillActivityService.getActivityDetail(testActivityId);
        assertThat(vo).isNotNull();
        assertThat(vo.getActivityId()).isEqualTo(testActivityId);
        assertThat(vo.getStock()).isEqualTo(100); //来自实体

        //验证查库一次
        verify(seckillActivityMapper, times(1)).selectById(testActivityId);
    }

    /**
     * 用例5：活动校验-活动进行中可参与
     */
    @Test
    void testCheckActivityRunning() {
        prepareProductMock();

        //活动状态为RUNNING(1)
        SeckillActivity activity = prepareActivityMock(true, LocalDateTime.now().plusHours(2));
        activity.setStatus(ActivityStatusEnum.RUNNING.getCode());

        //执行校验
        ActivityCheckResponse checkResponse = seckillActivityService.checkActivity(testActivityId, 1001L);
        assertThat(checkResponse).isNotNull();
        assertThat(checkResponse.getCanJoin()).isTrue();
        assertThat(checkResponse.getReason()).isEqualTo("ALLOW");
        assertThat(checkResponse.getActivityStatus()).isEqualTo(ActivityStatusEnum.RUNNING);
        assertThat(checkResponse.getActivityId()).isEqualTo(testActivityId);
        assertThat(checkResponse.getUserId()).isEqualTo(1001L);
    }

    /**
     * 用例6：活动结束不可参加
     */
    @Test
    void testCheckActivityEnded() {
        prepareProductMock();
        SeckillActivity activity = prepareActivityMock(true, LocalDateTime.now().plusHours(2));
        activity.setStatus(ActivityStatusEnum.ENDED.getCode());

        ActivityCheckResponse checkResponse = seckillActivityService.checkActivity(testActivityId, 1000L);
        assertThat(checkResponse).isNotNull();
        assertThat(checkResponse.getCanJoin()).isFalse();
        assertThat(checkResponse.getReason()).isEqualTo("ACTIVITY_ENDED");
        assertThat(checkResponse.getActivityStatus()).isEqualTo(ActivityStatusEnum.ENDED);
    }

    /**
     * 用例7：结束活动，无法预热，抛出异常
     */
    @Test
    void testPreheatRejectForEnded() {
        prepareProductMock();

        //活动结束时间为过去
        SeckillActivity activity = prepareActivityMock(false, LocalDateTime.now().minusHours(1));

        //执行预热抛出异常
        assertThatThrownBy(() -> seckillCacheService.preheatActivity(testActivityId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动已结束，无法预热");

        //验证未更新预热状态
        verify(seckillActivityMapper, never()).update(any(), any());
    }

    /**
     * 7.3 缓存穿透测试：连续100次请求不存在的商品，只查询一次数据库
     */
    @Test
    void testCachePenetration() {
        // mock：查询不存在的商品返回 null，模拟恶意/异常请求
        when(productMapper.selectById(eq(NON_EXISTENT_PRODUCT_ID))).thenReturn(null);

        // 连续查询 100 次
        for (int i = 0; i < 100; i++) {
            ProductVO result = productService.getProductDetail(NON_EXISTENT_PRODUCT_ID);
            assertThat(result).isNull();
        }

        // 验证数据库只被查询了一次（其余 99 次命中空值缓存）
        verify(productMapper, times(1)).selectById(NON_EXISTENT_PRODUCT_ID);

        // 断言 Redis 中存在空值标记
        String emptyKey = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + NON_EXISTENT_PRODUCT_ID;
        Object cachedValue = redisTemplate.opsForValue().get(emptyKey);
        assertThat(cachedValue).isEqualTo("");

        log.info("穿透测试通过：100次请求仅查库1次，空值标记已写入 Redis");
    }

    /**
     * 7.4 缓存击穿测试：8线程并发请求同一商品，只查询一次数据库
     */
    @Test
    void testCacheBreakdown() throws Exception {
        // 构造完整的商品对象（避免 convertToVO 时空指针）
        Product mockProduct = new Product();
        mockProduct.setId(EXISTENT_PRODUCT_ID);
        mockProduct.setName("测试商品");
        mockProduct.setDescription("用于击穿测试");
        mockProduct.setImageUrl("http://test.jpg");
        mockProduct.setOriginalPrice(10000L);
        mockProduct.setTotalStock(100);
        mockProduct.setAvailableStock(100);
        mockProduct.setStatus(ProductStatusEnum.ON_SHELF.getCode());

        // mock：模拟慢查询（300ms），拉开并发窗口
        when(productMapper.selectById(eq(EXISTENT_PRODUCT_ID))).thenAnswer(invocation -> {
            Thread.sleep(300);
            return mockProduct;
        });

        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ProductVO vo = productService.getProductDetail(EXISTENT_PRODUCT_ID);
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

        startLatch.countDown();

        // 等待所有线程完成（最多 10 秒）
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(exceptionRef.get()).isNull();

        executor.shutdown();

        // 验证数据库只被查询了一次
        verify(productMapper, times(1)).selectById(EXISTENT_PRODUCT_ID);

        // 验证锁已释放
        String lockKey = CacheKeyConstant.PRODUCT_LOCK_PREFIX + EXISTENT_PRODUCT_ID;
        assertThat(redisTemplate.hasKey(lockKey)).isFalse();

        // 验证缓存中已有真实商品
        String detailKey = CacheKeyConstant.PRODUCT_DETAIL_PREFIX + EXISTENT_PRODUCT_ID;
        Object cachedProduct = redisTemplate.opsForValue().get(detailKey);
        assertThat(cachedProduct).isInstanceOf(Product.class);
        Product cached = (Product) cachedProduct;
        assertThat(cached.getId()).isEqualTo(EXISTENT_PRODUCT_ID);

        log.info("击穿测试通过：8线程并发仅查库1次，锁已释放，缓存已回写");
    }

    private SeckillActivity prepareActivityMock(boolean useCache, LocalDateTime endTime) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(testActivityId);
        activity.setProductId(testProductId);
        activity.setActivityName("测试活动");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(endTime);
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(100);
        activity.setLimitPerUser(1);
        activity.setStatus(ActivityStatusEnum.NOT_STARTED.getCode());
        activity.setPreheatStatus(0); // 未预热
        activity.setVersion(0);

        // mock selectById
        when(seckillActivityMapper.selectById(testActivityId)).thenReturn(activity);

        // mock insert
        when(seckillActivityMapper.insert(any(SeckillActivity.class))).thenReturn(1);

        // mock update (用于预热更新状态)
        when(seckillActivityMapper.update(any(), any())).thenReturn(1);

        // 如果useCache为true，表示预热过，需要让缓存中存在（但预热方法会写缓存，我们可以在测试中显式调用预热，或直接模拟缓存内容）
        // 这里的useCache用于某些测试，但我们在测试中会调用preheatActivity，所以不需要提前缓存
        return activity;
    }

    private void prepareProductMock() {
        //仅mock selectById，让商品存在
        Product product = new Product();
        product.setId(testProductId);
        product.setName("测试商品");
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        when(productMapper.selectById(testProductId)).thenReturn(product);
    }

}
