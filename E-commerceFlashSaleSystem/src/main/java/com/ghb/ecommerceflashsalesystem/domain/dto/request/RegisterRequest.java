package com.ghb.ecommerceflashsalesystem.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "账号名不能为空")
    @Pattern(regexp = "(?i)[a-z0-9._-]{3,32}", message = "账号名须为 3 至 32 位字母、数字或 . _ -")
    private String accountName;

    @NotBlank(message = "密码不能为空")
    @Size(min = 10, max = 72, message = "密码长度须为 10 至 72 个字符")
    private String password;

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String displayName;
}
