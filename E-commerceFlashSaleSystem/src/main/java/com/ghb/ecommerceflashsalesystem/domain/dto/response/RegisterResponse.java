package com.ghb.ecommerceflashsalesystem.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 动态发令牌注册响应：返回本次登记的用户 ID 与 32 位随机访问令牌。
 * 令牌由服务端生成，客户端需保存后随 X-User-Token 请求头携带。
 */
@Data
@AllArgsConstructor
public class RegisterResponse {

    /** 本次注册成功的用户 ID */
    private Long userId;

    /** 服务端随机生成的访问令牌（32 位十六进制） */
    private String token;
}
