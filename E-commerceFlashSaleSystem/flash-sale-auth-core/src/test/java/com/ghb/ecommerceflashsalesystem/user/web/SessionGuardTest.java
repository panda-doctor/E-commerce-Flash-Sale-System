package com.ghb.ecommerceflashsalesystem.user.web;

import com.ghb.ecommerceflashsalesystem.contracts.auth.SessionValidationPort;
import com.ghb.ecommerceflashsalesystem.user.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SessionGuard} 的短 TTL 缓存语义（P4 收官后优化①）。
 *
 * <p>三条不变量：① 默认 {@code 0s} = **关闭**（每次请求都真校验，行为与缓存引入前一致）；
 * ② 开启后同一 token 在 TTL 内只打一次下游；③ TTL 过后**必须重新校验**（吊销延迟 ≤ TTL，
 * 不能退化成永久缓存）。</p>
 *
 * <p>刻意不用 Mockito 的 verify 家族：用一个手写计数端口统计真实校验次数，
 * 断言直接落在"下游被调用几次"这个可观测事实上。</p>
 */
class SessionGuardTest {

    private static final String COOKIE_NAME = "SESSION";
    private static final String RAW_TOKEN = "raw-token-for-test";
    private static final Long USER_ID = 7L;

    @Test
    @DisplayName("默认 TTL=0：每次请求都真校验（缓存关闭）")
    void disabledByDefault() {
        Fixture f = new Fixture(Duration.ZERO);

        assertThat(f.guard.authenticate(f.request)).isNotNull();
        assertThat(f.guard.authenticate(f.request)).isNotNull();

        assertThat(f.port.calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("开启缓存：TTL 内同一 token 只校验一次")
    void cachesWithinTtl() {
        Fixture f = new Fixture(Duration.ofSeconds(5));

        assertThat(f.guard.authenticate(f.request)).isNotNull();
        assertThat(f.guard.authenticate(f.request)).isNotNull();

        assertThat(f.port.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("TTL 过后重新校验（缓存不会变永久）")
    void revalidatesAfterTtl() throws Exception {
        Fixture f = new Fixture(Duration.ofMillis(30));

        f.guard.authenticate(f.request);
        Thread.sleep(150);
        f.guard.authenticate(f.request);

        assertThat(f.port.calls.get()).isEqualTo(2);
    }

    /** 统一装配：计数端口 + 真实 SecurityProperties（反射设 cookie 名）+ 反射设 TTL。 */
    private static final class Fixture {
        final CountingPort port = new CountingPort();
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final SessionGuard guard;

        Fixture(Duration ttl) {
            SecurityProperties props = new SecurityProperties();
            setField(props, SecurityProperties.class, "sessionCookieName", COOKIE_NAME);
            when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, RAW_TOKEN)});
            this.guard = new SessionGuard(port, props);
            setField(this.guard, SessionGuard.class, "sessionCacheTtl", ttl);
        }

        private static void setField(Object target, Class<?> type, String name, Object value) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法设置字段 " + name, e);
            }
        }
    }

    /** 手写端口：统计被真实校验的次数（缓存命中时不应增加）。 */
    private static final class CountingPort implements SessionValidationPort {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Optional<SessionView> validate(String tokenHash) {
            calls.incrementAndGet();
            return Optional.of(new SessionView(USER_ID, "csrf-hash"));
        }
    }
}
