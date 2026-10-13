package com.ghb.ecommerceflashsalesystem.user.controller;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.user.dto.request.LoginRequest;
import com.ghb.ecommerceflashsalesystem.user.dto.request.RegisterRequest;
import com.ghb.ecommerceflashsalesystem.user.dto.response.AuthResponse;
import com.ghb.ecommerceflashsalesystem.user.dto.response.UserProfileResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import com.ghb.ecommerceflashsalesystem.user.service.UserAccountService;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService.AuthenticatedSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {
    private final UserAccountService userAccountService;
    private final UserSessionService userSessionService;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        UserAccount user = userAccountService.register(request);
        String csrfToken = userSessionService.create(user, response);
        return Result.success(new AuthResponse(UserProfileResponse.from(user), csrfToken));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        UserAccount user = userAccountService.authenticate(request.getAccountName(), request.getPassword());
        String csrfToken = userSessionService.create(user, response);
        return Result.success(new AuthResponse(UserProfileResponse.from(user), csrfToken));
    }

    @GetMapping("/me")
    public Result<UserProfileResponse> me(HttpServletRequest request) {
        Long userId = authenticatedUserId(request);
        return Result.success(userAccountService.profile(userId));
    }

    @GetMapping("/csrf")
    public Result<String> csrf(HttpServletRequest request, HttpServletResponse response) {
        Object value = request.getAttribute(UserSessionService.AUTH_SESSION_ATTRIBUTE);
        if (!(value instanceof AuthenticatedSession session)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return Result.success(userSessionService.rotateCsrf(session, response));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        userSessionService.revoke(request, response);
        return Result.success(null);
    }

    private Long authenticatedUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(UserSessionService.AUTHENTICATED_USER_ID);
        if (!(userId instanceof Long id)) throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        return id;
    }
}
