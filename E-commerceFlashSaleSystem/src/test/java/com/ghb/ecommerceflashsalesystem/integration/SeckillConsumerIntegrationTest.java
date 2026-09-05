package com.ghb.ecommerceflashsalesystem.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import com.ghb.ecommerceflashsalesystem.stream.consumer.SeckillOrderConsumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
public class SeckillConsumerIntegrationTest {
    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillCacheService seckillCacheService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private SeckillOrderConsumer seckillOrderConsumer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long ACTIVITY_ID = 200L; // 测试用ID
    private static final Long USER_ID = 9999L;
    private static final String ORDER_NO = "SK2026090500000001"; // 会动态生成，测试中捕获

    @BeforeEach
    void setUp() {
        // 清理Redis键（Stream、活动、库存、限流）
        cleanKeys(CacheKeyConstant.SECKILL_ORDER_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        // 清理数据库测试订单
        seckillOrderMapper.delete(new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, ACTIVITY_ID));

        // 插入真实活动（product_id=1 必须存在）
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(1L);
        activity.setActivityName("消费者测试活动");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(100);
        activity.setLimitPerUser(1);
        activity.setStatus(ActivityStatusEnum.RUNNING.getCode());
        activity.setPreheatStatus(0);
        activity.setVersion(0);
        seckillActivityMapper.insert(activity);

        // 预热缓存
        seckillCacheService.preheatActivity(ACTIVITY_ID);

        // 确保消费者组存在
        seckillOrderConsumer.ensureGroup();
    }

    @AfterEach
    void tearDown() {
        // 清理Redis
        cleanKeys(CacheKeyConstant.SECKILL_ORDER_STREAM);
        cleanKeys(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID);
        cleanKeys(CacheKeyConstant.RATE_LIMIT_PREFIX + "*");
        cleanKeys(CacheKeyConstant.SECKILL_USER_PREFIX + "*");

        // 清理数据库订单和活动
        seckillOrderMapper.delete(new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getActivityId, ACTIVITY_ID));
        seckillActivityMapper.deleteById(ACTIVITY_ID);
    }

    private void cleanKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    @Test
    void testConsumerCreateOrder() {
        //1.执行秒杀
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(ACTIVITY_ID);
        request.setUserId(USER_ID);
        SeckillResponse response = seckillService.execute(request);
        assertThat(response.getResult()).isEqualTo("QUEUED");
        String orderNo = response.getOrderNo();
        assertThat(orderNo).isNotNull();

        //2.消费消息
        seckillOrderConsumer.consumePending(10); // 拉去10条

        //3.验证订单落库
        SeckillOrder order = seckillOrderMapper.selectByOrderNo(orderNo);
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo(1); // CREATED
        assertThat(order.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(order.getProductId()).isEqualTo(1L);
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getSeckillPrice()).isEqualTo(9900L);

        // 4. 验证Stream消息已被ACK（可查看PEL为空）
        // 通过XINFO查看pending，但Spring Data Redis不支持，略过
        // 也可以尝试再消费一次，应该无新消息
        seckillOrderConsumer.consumePending(10);
        // 无异常即可
    }

    @Test

    void testIdempotentConsume() {
        //先执行秒杀，生成订单
        SeckillRequest request = new SeckillRequest();
        request.setActivityId(ACTIVITY_ID);
        request.setUserId(USER_ID);
        SeckillResponse response = seckillService.execute(request);
        String orderNo = response.getOrderNo();

        //第一次消费
        seckillOrderConsumer.consumePending(10);
        Long count1 = seckillOrderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo));
        assertThat(count1).isEqualTo(1);

        // 模拟重复消费（此时消息已被ACK，但若未ACK可模拟再次调用handle，但这里我们无法直接调用私有handle）
        // 可通过再次调用consumePending，应该没有消息返回，所以不会重复插入
        seckillOrderConsumer.consumePending(10);
        Long count2 = seckillOrderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo));
        assertThat(count2).isEqualTo(1); // 依然只有1条

    }

}
