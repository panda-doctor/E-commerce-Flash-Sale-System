package com.ghb.ecommerceflashsalesystem.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private UserProfileResponse user;
    /** 防跨站请求令牌仅返回给浏览器内存，并随写操作放入 X-CSRF-Token 请求头。 */
    private String csrfToken;
}
