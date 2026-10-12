package com.ghb.ecommerceflashsalesystem.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "账号名不能为空")
    @Pattern(regexp = "(?i)[a-z0-9._-]{3,32}", message = "账号名格式无效")
    private String accountName;

    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码格式无效")
    private String password;
}
