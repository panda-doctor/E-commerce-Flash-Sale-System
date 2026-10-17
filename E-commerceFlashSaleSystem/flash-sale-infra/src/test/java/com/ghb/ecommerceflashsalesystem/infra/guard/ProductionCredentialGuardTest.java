package com.ghb.ecommerceflashsalesystem.infra.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审查整改 R3：生产凭据校验的语义测试（纯单测，不启 Spring 上下文）。
 */
class ProductionCredentialGuardTest {

    private static StandardEnvironment env(Map<String, Object> props) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", props));
        return environment;
    }

    private static Map<String, Object> prodWithDefaults() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.profiles.active", "prod");
        props.put("module.internal.token", "dev-internal-token");
        props.put("spring.datasource.password", "123456");
        return props;
    }

    @Test
    @DisplayName("prod + 开发默认凭据 → 启动失败")
    void productionWithDefaultsFailsFast() {
        ProductionCredentialGuard guard = new ProductionCredentialGuard(env(prodWithDefaults()));
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("module.internal.token")
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("prod + 显式凭据 → 通过（且无数据源时不校验口令）")
    void productionWithExplicitCredentialsPasses() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.profiles.active", "prod");
        props.put("module.internal.token", "a-real-internal-token");
        // 故意不配 spring.datasource.password：模拟单体（无库）
        ProductionCredentialGuard guard = new ProductionCredentialGuard(env(props));
        assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非 prod → 即使使用默认值也放行（本地/测试零影响）")
    void devProfileIsNotChecked() {
        Map<String, Object> props = prodWithDefaults();
        props.put("spring.profiles.active", "dev");
        ProductionCredentialGuard guard = new ProductionCredentialGuard(env(props));
        assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }
}
