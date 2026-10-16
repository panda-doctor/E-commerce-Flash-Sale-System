package com.ghb.ecommerceflashsalesystem.contracts.auth;

import java.util.Optional;

/**
 * 会话校验端口（P4 Day 7）：把"会话是否有效"从**本地查库**抽象出来，跨服务共用。
 *
 * <p><b>为什么放在 contracts 而不是 auth-core</b>：实现方有两类 —— auth 应用内的本地实现
 * （要 auth-core 的 mapper）和**校验方**的 HTTP 实现（在 {@code flash-sale-remote} 里，
 * 供 monolith / seckill / order 复用）。若把接口放在 auth-core，remote 就得依赖 auth-core，
 * 而 auth-core 带 {@code ApiAccessWebConfig} 等 Web 装配 —— 会**顺带**给 ai / activity /
 * product 这些本该只读接口的应用装上会话拦截器。放在最底层的 contracts 里，谁都能实现、谁都不被牵连。</p>
 *
 * <p><b>为什么需要这个端口</b>：`ApiAccessInterceptor` 活在 auth-core 共享库里，被
 * monolith / seckill / order 三个运行时宿主 —— Day 7 之前它靠 auth 的 mapper 直查
 * `user_session`，于是每个运行时都必须能连 auth 的表（order 为此背了双数据源桥、
 * seckill 应用整库指旧库）。跨服务化之后：</p>
 *
 * <ul>
 *   <li><b>auth 应用</b>（权威）：{@code module.auth.mode=db} → {@code LocalSessionValidationAdapter} 查自己的库；</li>
 *   <li><b>其余运行时</b>（默认 {@code remote}）：{@code HttpSessionValidationAdapter} 调
 *       auth 应用的内部接口 {@code POST /internal/auth/sessions/validate}。</li>
 * </ul>
 *
 * <p><b>失败语义</b>：实现必须区分"<b>查无此会话</b>"（返回 {@link Optional#empty()}）与
 * "<b>校验动作本身失败</b>"（抛异常，fail-closed）—— 后者绝不能被翻译成"放行"。</p>
 */
public interface SessionValidationPort {

    /**
     * 校验会话令牌哈希。
     *
     * @param tokenHash 令牌的 sha256（cookie 原文不经网络，只有哈希过线）
     * @return 有效会话视图；无效/已吊销/已过期 → {@link Optional#empty()}
     */
    Optional<SessionView> validate(String tokenHash);

    /** 会话视图：校验方需要的全部字段（userId + 用于 CSRF 比对的哈希） */
    record SessionView(Long userId, String csrfTokenHash) {
    }
}
