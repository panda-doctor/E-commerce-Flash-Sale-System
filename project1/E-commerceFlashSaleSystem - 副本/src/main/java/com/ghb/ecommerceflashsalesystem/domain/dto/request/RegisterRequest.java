package com.ghb.ecommerceflashsalesystem.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 动态发令牌注册请求（演示环境：客户端自选一个未占用的用户 ID，换取服务端随机令牌）。
 */
@Data
public class RegisterRequest {

    @NotNull(message = "userId 不能为空")
    @Positive(message = "userId 必须为正整数")
    private Long userId;
}
