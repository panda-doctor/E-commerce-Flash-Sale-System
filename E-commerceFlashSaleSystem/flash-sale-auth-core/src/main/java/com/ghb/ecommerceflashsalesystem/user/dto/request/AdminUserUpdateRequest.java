package com.ghb.ecommerceflashsalesystem.user.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {
    @Size(max = 64)
    private String displayName;
    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;
    @Size(min = 10, max = 72, message = "如需重置密码，长度须为 10 至 72 个字符")
    private String password;
}
