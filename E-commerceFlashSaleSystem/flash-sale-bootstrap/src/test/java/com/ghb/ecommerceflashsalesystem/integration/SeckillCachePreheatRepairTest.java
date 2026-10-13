package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.activity.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.activity.cache.SeckillCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证数据库预热标记与实际缓存不一致时，重试可以修复缓存。 */
@ExtendWith(MockitoExtension.class)
class SeckillCachePreheatRepairTest {

    private static final Long ACTIVITY_ID = 88002L;

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private SeckillActivityMapper activityMapper;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;

    private SeckillCacheService cacheService;

    @BeforeEach
    void setUp() throws InterruptedException {
        cacheService = new SeckillCacheService(redisTemplate, activityMapper, redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(buildPreheatedActivity());
    }

    @Test
    void preheat_repairsMissingStockKeyEvenWhenDatabaseSaysPreheated() {
        String activityKey = CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID;
        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        when(redisTemplate.hasKey(activityKey)).thenReturn(true);
        when(redisTemplate.hasKey(stockKey)).thenReturn(false);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(activityMapper.update(any(), any())).thenReturn(1);

        cacheService.preheatActivity(ACTIVITY_ID);

        verify(hashOperations).putAll(eq(activityKey), any());
        verify(valueOperations).set(stockKey, 12);
        verify(redisTemplate).expire(eq(activityKey), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisTemplate).expire(eq(stockKey), anyLong(), eq(TimeUnit.SECONDS));
        verify(activityMapper).update(any(), any());
    }

    @Test
    void preheat_rejectsOnlyWhenDatabaseAndBothCacheKeysAreReady() {
        when(redisTemplate.hasKey(CacheKeyConstant.SECKILL_ACTIVITY_PREFIX + ACTIVITY_ID)).thenReturn(true);
        when(redisTemplate.hasKey(CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID)).thenReturn(true);

        assertThatThrownBy(() -> cacheService.preheatActivity(ACTIVITY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .hasMessageContaining("活动已预热，请勿重复预热");

        verify(redisTemplate, never()).opsForHash();
        verify(redisTemplate, never()).opsForValue();
        verify(activityMapper, never()).update(any(), any());
    }

    private SeckillActivity buildPreheatedActivity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setProductId(100L);
        activity.setActivityName("预热修复测试活动");
        activity.setStartTime(LocalDateTime.now().plusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setSeckillPrice(9900L);
        activity.setSeckillStock(12);
        activity.setLimitPerUser(1);
        activity.setStatus(0);
        activity.setPreheatStatus(1);
        return activity;
    }
}
