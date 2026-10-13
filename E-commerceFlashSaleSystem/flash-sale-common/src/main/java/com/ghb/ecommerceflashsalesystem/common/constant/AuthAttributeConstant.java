package com.ghb.ecommerceflashsalesystem.common.constant;

/**
 * 鉴权上下文常量（跨模块共享）。
 *
 * <p>P0 解耦说明：请求 attribute 的键名原先定义在装配层的 {@code ApiAccessInterceptor} 上，
 * 控制器（seckill / order 域）引用它会造成业务模块反向依赖 bootstrap。上移到 common 后，
 * 鉴权写入方（拦截器）与读取方（控制器）都只依赖 common。
 */
public final class AuthAttributeConstant {

    private AuthAttributeConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 鉴权通过后写入 HttpServletRequest attribute 的 userId 键名 */
    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
}
