package com.ghb.ecommerceflashsalesystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户令牌注册表：静态白名单 + 动态注册表的统一身份源（R3 鉴权增强）。
 *
 * <p>身份来源分两层：
 * <ol>
 *   <li><b>静态层</b>：构造时解析 {@link SecurityProperties#getUserTokens()}（token:userId 逗号分隔），
 *       不可变，对应 .env 的演示六账号；</li>
 *   <li><b>动态层</b>：{@link #register(Long)} 运行时登记（内存实现，后端重启即失效），
 *       用于前端「自定义 userId → 领取令牌」的动态发令牌能力，避免每次新增演示账号都改 .env 与前端账号表。</li>
 * </ol>
 *
 * <p>安全设计：令牌一律服务端随机生成（32 位十六进制），不接收客户端传入的令牌；
 * 已占用 userId（静态账号或已动态注册者）拒绝再次注册，防止无凭证环境下的冒领。
 */
@Slf4j
@Component
public class UserTokenRegistry {

    /** 静态白名单（token -> uid），构造后不可变 */
    private final Map<String, Long> staticTokenToUid;
    /** 静态已占用的 uid 集合，加速占用判断 */
    private final Set<Long> staticUidSet;
    /** 动态注册表主表：uid -> token */
    private final Map<Long, String> dynamicUidToToken = new ConcurrentHashMap<>();
    /** 动态注册表反查索引：token -> uid（按令牌解析身份用） */
    private final Map<String, Long> dynamicTokenToUid = new ConcurrentHashMap<>();

    public UserTokenRegistry(SecurityProperties securityProperties) {
        this.staticTokenToUid = parseStaticTokens(securityProperties.getUserTokens());
        this.staticUidSet = Set.copyOf(staticTokenToUid.values());
        log.info("UserTokenRegistry 初始化完成：静态令牌 {} 条，占用 uid {} 个",
                staticTokenToUid.size(), staticUidSet.size());
    }

    /**
     * 把配置串解析为不可变静态映射；格式非法或 uid 非正整数的条目跳过并告警。
     */
    private static Map<String, Long> parseStaticTokens(String raw) {
        Map<String, Long> map = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return Map.copyOf(map);
        }
        for (String item : raw.split(",")) {
            String[] parts = item.trim().split(":", 2);
            if (parts.length != 2) {
                log.warn("忽略非法静态令牌配置：{}", item);
                continue;
            }
            String token = parts[0].trim();
            if (token.isEmpty()) {
                log.warn("忽略空令牌的静态配置：{}", item);
                continue;
            }
            try {
                long uid = Long.parseLong(parts[1].trim());
                if (uid > 0) {
                    map.put(token, uid);
                } else {
                    log.warn("忽略 uid 非正整数的静态配置：{}", item);
                }
            } catch (NumberFormatException e) {
                log.warn("忽略 uid 非数字的静态配置：{}", item);
            }
        }
        return Map.copyOf(map);
    }

    /**
     * 令牌 -> 用户标识：先静态后动态，命中返回 uid，否则空。
     */
    public Optional<Long> resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Long uid = staticTokenToUid.get(token);
        if (uid != null) {
            return Optional.of(uid);
        }
        return Optional.ofNullable(dynamicTokenToUid.get(token));
    }

    /**
     * userId 是否已被占用（静态账号或已动态注册者）。
     */
    public boolean isOccupied(Long uid) {
        return uid != null && (staticUidSet.contains(uid) || dynamicUidToToken.containsKey(uid));
    }

    /**
     * 动态注册：uid 未占用则发放服务端随机令牌并登记，返回令牌；已占用返回空。
     *
     * <p>并发与一致性：演示环境注册频率极低，方法级同步即可同时保证「同 uid 仅成功一次」
     * 与「随机令牌撞车时重试」的原子性，避免无锁抢占方案里的复合竞态。
     */
    public synchronized Optional<String> register(Long uid) {
        if (isOccupied(uid)) {
            log.warn("动态注册被拒绝：userId {} 已被占用（静态账号或已注册）", uid);
            return Optional.empty();
        }
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (dynamicTokenToUid.containsKey(token));
        dynamicUidToToken.put(uid, token);
        dynamicTokenToUid.put(token, uid);
        log.info("动态注册成功：userId {} 已领取 32 位随机令牌", uid);
        return Optional.of(token);
    }
}
