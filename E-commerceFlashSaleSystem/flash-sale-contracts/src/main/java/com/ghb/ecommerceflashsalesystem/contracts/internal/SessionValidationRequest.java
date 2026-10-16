package com.ghb.ecommerceflashsalesystem.contracts.internal;

/**
 * 会话校验内部接口的请求体（P4 Day 7）。
 *
 * <p>只传**令牌哈希**（sha256），cookie 原文不出进程 —— 内部接口也不该拿到可直接冒用的凭据。</p>
 */
public record SessionValidationRequest(String tokenHash) {
}
