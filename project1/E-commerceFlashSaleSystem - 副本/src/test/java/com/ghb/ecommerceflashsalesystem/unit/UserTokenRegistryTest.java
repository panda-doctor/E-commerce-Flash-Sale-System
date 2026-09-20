package com.ghb.ecommerceflashsalesystem.unit;

import com.ghb.ecommerceflashsalesystem.config.SecurityProperties;
import com.ghb.ecommerceflashsalesystem.config.UserTokenRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动态发令牌注册表单元测试（纯单测，不启动 Spring）。
 *
 * <p>覆盖：静态白名单解析 / 动态注册与按令牌反查 / 防冒领（静态账号、重复注册、并发抢注）/
 * 令牌为服务端生成的 32 位十六进制随机串。
 */
public class UserTokenRegistryTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    private UserTokenRegistry build(String userTokens) {
        SecurityProperties props = new SecurityProperties();
        props.setUserTokens(userTokens);
        return new UserTokenRegistry(props);
    }

    // 1. 静态白名单解析：token -> uid；容忍空白、忽略非法行（uid 非数字 / 缺冒号）
    @Test
    void staticTokensResolvedFromConfig() {
        UserTokenRegistry registry = build(" user-a:1001 ,user-b:1002,bad:abc,user-c: 1003,orphan");
        assertThat(registry.resolveUserId("user-a")).hasValue(1001L);
        assertThat(registry.resolveUserId("user-b")).hasValue(1002L);
        assertThat(registry.resolveUserId("user-c")).hasValue(1003L);
        assertThat(registry.resolveUserId("bad")).isEmpty();    // uid 非数字：整条忽略
        assertThat(registry.resolveUserId("orphan")).isEmpty(); // 缺冒号分隔：整条忽略
        assertThat(registry.isOccupied(1001L)).isTrue();
    }

    // 2. 动态注册：返回服务端随机 32 位令牌；令牌可反查 uid；占用标记生效
    @Test
    void dynamicRegisterIssuesTokenAndResolves() {
        UserTokenRegistry registry = build("");
        Optional<String> tokenOpt = registry.register(9001L);

        assertThat(tokenOpt).isPresent();
        assertThat(TOKEN_PATTERN.matcher(tokenOpt.get()).matches()).isTrue();
        assertThat(registry.resolveUserId(tokenOpt.get())).hasValue(9001L);
        assertThat(registry.isOccupied(9001L)).isTrue();
    }

    // 3. 防冒领：静态账号的 uid 不可被动态注册
    @Test
    void staticUidCannotBeRegisteredDynamically() {
        UserTokenRegistry registry = build("user-a:1001");
        assertThat(registry.register(1001L)).isEmpty();
        assertThat(registry.isOccupied(1001L)).isTrue();
    }

    // 4. 防冒领：同一 uid 重复注册被拒
    @Test
    void duplicateUidRegisterRejected() {
        UserTokenRegistry registry = build("");
        assertThat(registry.register(9002L)).isPresent();
        assertThat(registry.register(9002L)).isEmpty();
    }

    // 5. 并发抢注同一 uid：仅一个线程成功
    @Test
    void concurrentRegisterOnlyOneSucceeds() throws InterruptedException {
        UserTokenRegistry registry = build("");
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (registry.register(9100L).isPresent()) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertThat(success.get()).isEqualTo(1);
        assertThat(registry.isOccupied(9100L)).isTrue();
    }

    // 6. 空 / 未知令牌一律解析不到身份
    @Test
    void blankOrUnknownTokenCannotResolve() {
        UserTokenRegistry registry = build("user-a:1001");
        assertThat(registry.resolveUserId(null)).isEmpty();
        assertThat(registry.resolveUserId("  ")).isEmpty();
        assertThat(registry.resolveUserId("not-exist")).isEmpty();
    }
}
