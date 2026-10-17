package com.ghb.ecommerceflashsalesystem.infra.guard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 审查整改 R3（P1-3）：**生产 profile 下禁止使用开发默认凭据**。
 *
 * <p><b>要解决的问题</b>：`module.internal.token` 默认 `dev-internal-token`、
 * `spring.datasource.password` 兜底 `123456`。部署时漏配环境变量，服务会**照常启动**并按默认值
 * 接受 `/internal/**` 调用（可被猜中令牌重建投影、读库存）—— 这是审查报告里的 P1-3。</p>
 *
 * <p><b>策略</b>：只在 {@code prod} profile 生效（本地/测试零影响）：
 * <ul>
 *   <li>内部令牌为空或等于开发默认值 → **启动失败**（不是警告：默认令牌等于把内部接口暴露给猜中者）；</li>
 *   <li>数据库口令等于 `123456` 且该应用确实配了数据源 → 启动失败；</li>
 *   <li>单体已无数据源（{@code mysql=ABSENT}）→ 该检查自动跳过。</li>
 * </ul></p>
 *
 * <p><b>为什么在 infra</b>：八个运行时的启动类都扫描 {@code com.ghb.ecommerceflashsalesystem}，
 * 放在 infra 一处即可覆盖全部应用，无需逐应用复制。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionCredentialGuard implements ApplicationRunner {

    /** 与各 application.yaml 的默认值保持一致（改动 yaml 默认值时必须同步这里）。 */
    static final String DEV_INTERNAL_TOKEN = "dev-internal-token";
    static final String DEV_DB_PASSWORD = "123456";

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!isProduction()) {
            log.debug("非 prod profile，跳过生产凭据校验");
            return;
        }
        List<String> problems = new ArrayList<>();

        String internalToken = environment.getProperty("module.internal.token", "");
        if (internalToken.isBlank() || DEV_INTERNAL_TOKEN.equals(internalToken)) {
            problems.add("module.internal.token 为空或仍是开发默认值（" + DEV_INTERNAL_TOKEN
                    + "）→ /internal/** 可被猜中令牌访问，请显式设置 INTERNAL_TOKEN");
        }

        // 仅当该应用真的配了数据源时才校验（单体无库，此项为 null）
        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword != null && DEV_DB_PASSWORD.equals(dbPassword)) {
            problems.add("spring.datasource.password 仍是开发默认值（" + DEV_DB_PASSWORD
                    + "）→ 请显式设置 MYSQL_PASSWORD");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("生产配置校验失败（P1-3 整改项）：\n  - " + String.join("\n  - ", problems));
        }
        log.info("生产凭据校验通过（内部令牌与数据库口令均非开发默认值）");
    }

    private boolean isProduction() {
        String profiles = environment.getProperty("spring.profiles.active", "");
        return Arrays.stream(profiles.split(","))
                .map(String::trim)
                .anyMatch("prod"::equals);
    }
}
