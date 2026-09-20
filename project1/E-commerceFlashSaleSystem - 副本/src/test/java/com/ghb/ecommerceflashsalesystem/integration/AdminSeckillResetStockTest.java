package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.cache.SeckillCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * E1-r · 库存重置守卫单测（方案 A：重置库存接入管理端口的守卫语义）
 *
 * 覆盖 SeckillCacheService#resetStock 的三条核心语义：
 * - 活动不存在 → NOT_FOUND；
 * - 活动进行中 → PARAM_ERROR，且【不得触碰 Redis】（防止把已扣减库存"复活"）；
 * - 未开始（/已结束）→ 放行：把 DB 配置库存写回缓存。
 *
 * 说明：纯 Mockito 单测（不启容器），手动组装 SeckillCacheService。
 * 分布式锁由 mock 的 Redisson 放行，聚焦"守卫拦截 / 放行写回"两条路径。
 */
@ExtendWith(MockitoExtension.class)
public class AdminSeckillResetStockTest {

    private static final Long ACTIVITY_ID = 88001L;
    private static final int CONFIG_STOCK = 10;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private SeckillCacheService seckillCacheService;

    @BeforeEach
    void setUp() throws Exception {
        seckillCacheService = new SeckillCacheService(redisTemplate, seckillActivityMapper, redissonClient);
        // 重置方法内部先获取分布式锁；单测里直接放行，聚焦守卫与写回语义
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    /** 活动不存在：NOT_FOUND，不得有任何缓存写入 */
    @Test
    void reset_notExist_throwsNotFound() {
        when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

        assertThatThrownBy(() -> seckillCacheService.resetStock(ACTIVITY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
        verifyNoInteractions(redisTemplate);
    }

    /** 活动进行中：PARAM_ERROR 拦截，且守卫须在写 Redis 之前拦下（禁止复活已扣减库存） */
    @Test
    void reset_activityRunning_rejectedWithoutTouchingRedis() {
        LocalDateTime now = LocalDateTime.now();
        when(seckillActivityMapper.selectById(ACTIVITY_ID))
                .thenReturn(buildActivity(now.minusHours(1), now.plusHours(1)));

        assertThatThrownBy(() -> seckillCacheService.resetStock(ACTIVITY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                            assertThat(e.getMessage()).contains("活动进行中，禁止重置库存");
                        });
        // 守卫在 set 之前抛异常，Redis 不得有任何交互
        verifyNoInteractions(redisTemplate);
    }

    /** 未开始：放行，DB 配置库存写回缓存键并刷新 TTL */
    @Test
    void reset_notStarted_writeBackConfigStock() {
        LocalDateTime now = LocalDateTime.now();
        when(seckillActivityMapper.selectById(ACTIVITY_ID))
                .thenReturn(buildActivity(now.plusHours(1), now.plusHours(2)));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        seckillCacheService.resetStock(ACTIVITY_ID);

        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        verify(valueOps).set(eq(stockKey), eq(CONFIG_STOCK));
        verify(redisTemplate).expire(eq(stockKey), anyLong(), eq(TimeUnit.SECONDS));
    }

    /** 已结束：同样放行（运维复位场景），不校验 TTL 细节只断言库存写回 */
    @Test
    void reset_alreadyEnded_writeBackConfigStock() {
        LocalDateTime now = LocalDateTime.now();
        when(seckillActivityMapper.selectById(ACTIVITY_ID))
                .thenReturn(buildActivity(now.minusHours(2), now.minusHours(1)));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        seckillCacheService.resetStock(ACTIVITY_ID);

        String stockKey = CacheKeyConstant.SECKILL_STOCK_PREFIX + ACTIVITY_ID;
        verify(valueOps).set(eq(stockKey), eq(CONFIG_STOCK));
        // 已结束活动 TTL 计算可能为 0（取决于 EXTRA 缓冲），不断言 expire
        verify(redisTemplate, never()).delete(anyString());
    }

    private SeckillActivity buildActivity(LocalDateTime startTime, LocalDateTime endTime) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setSeckillStock(CONFIG_STOCK);
        return activity;
    }
}
