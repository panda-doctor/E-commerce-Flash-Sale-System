package com.ghb.ecommerceflashsalesystem.user.controller;


import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.user.dto.request.AdminUserCreateRequest;
import com.ghb.ecommerceflashsalesystem.user.dto.request.AdminUserUpdateRequest;
import com.ghb.ecommerceflashsalesystem.common.api.PageResponse;
import com.ghb.ecommerceflashsalesystem.user.dto.response.UserProfileResponse;
import com.ghb.ecommerceflashsalesystem.user.service.UserAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {
    private final UserAccountService userAccountService;

    @GetMapping
    public Result<PageResponse<UserProfileResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status) {
        return Result.success(userAccountService.page(page, pageSize, keyword, status));
    }

    @GetMapping("/{userId}")
    public Result<UserProfileResponse> get(@PathVariable Long userId) {
        return Result.success(userAccountService.adminGet(userId));
    }

    @PostMapping
    public Result<UserProfileResponse> create(@Valid @RequestBody AdminUserCreateRequest request) {
        return Result.success(userAccountService.adminCreate(request));
    }

    @PutMapping("/{userId}")
    public Result<UserProfileResponse> update(@PathVariable Long userId,
                                              @Valid @RequestBody AdminUserUpdateRequest request) {
        return Result.success(userAccountService.adminUpdate(userId, request));
    }

    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable Long userId) {
        userAccountService.softDelete(userId);
        return Result.success(null);
    }
}
