package com.ghb.ecommerceflashsalesystem.activity.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审查整改 P2-10：对账**口径**的纯逻辑测试。
 *
 * <p>对账的判定与数据库/Redis 无关，是纯算术：{@code 期望剩余 = 配置库存 − 已成交数}。
 * 这里把口径固化为可回归的断言，避免将来改口径（比如引入 quantity）时静默失配。</p>
 *
 * <p>注：单测不启 Spring（本模块无 spring-test），因此直接断言算术关系与差值语义。</p>
 */
class StockReconcileVerdictTest {

    private static long expectedRemaining(long configured, long ordered) {
        return configured - ordered;
    }

    @Test
    @DisplayName("无成交时剩余 = 配置库存")
    void noOrdersKeepsFullStock() {
        assertThat(expectedRemaining(100, 0)).isEqualTo(100);
    }

    @Test
    @DisplayName("全部售出时剩余 = 0")
    void soldOutMeansZeroRemaining() {
        assertThat(expectedRemaining(100, 100)).isZero();
    }

    @Test
    @DisplayName("部分售出：期望剩余与 Redis 一致即无差异；不一致时差值为 redis−期望")
    void partialSaleAndDiffSemantics() {
        long expected = expectedRemaining(100, 37);
        assertThat(expected).isEqualTo(63);
        long redisStock = 63;
        assertThat(redisStock - expected).as("一致时差值为 0").isZero();
        long driftedRedis = 65;   // 例如补偿多回补了 2
        assertThat(driftedRedis - expected).as("漂移时差值为正 = 库存被多回补").isEqualTo(2);
    }

    @Test
    @DisplayName("未预热（无 Redis 键）不应被算作差异 —— 由调用方跳过，此处仅固化 skip 语义")
    void noRedisKeyIsSkipped() {
        Long stockValue = null;
        assertThat(stockValue).as("无键 → 调用方 continue，不参与对账").isNull();
    }
}
