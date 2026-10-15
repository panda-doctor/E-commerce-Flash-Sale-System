package com.ghb.ecommerceflashsalesystem.infra.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部接口令牌配置（P3 Day 5）。
 *
 * <p>{@code /internal/**} 是<b>服务间</b>调用入口（不经网关，网关的准入对它无效），因此必须自带
 * 令牌校验：{@code X-Internal-Token} 由调用方（如 activity 应用）携带，被调方（如单体的 seckill 域）
 * 校验。</p>
 *
 * <p><b>fail-closed</b>：未配置时拒绝所有 {@code /internal/**} 请求 —— 否则等于把"库存写口"这类
 * 内部能力暴露给内网任意进程。</p>
 */
@Data
@ConfigurationProperties(prefix = "module.internal")
public class InternalTokenProperties {

    /** 内部接口令牌（请求头 {@code X-Internal-Token}）。未配置时所有 /internal/** 一律拒绝。 */
    private String token;
}
