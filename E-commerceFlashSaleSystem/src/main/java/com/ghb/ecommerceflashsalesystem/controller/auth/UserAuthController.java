package com.ghb.ecommerceflashsalesystem.controller.auth;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.config.UserTokenRegistry;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.RegisterRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.RegisterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 动态发令牌接口（R3 鉴权增强：内存注册表，后端重启后已发放令牌失效，可重新注册）。
 *
 * <p>演示环境没有账号系统，本接口是「顶栏自定义 userId → 领取令牌」的能力出口。
 * 安全说明：接口位于拦截路径之外匿名可调，因此防冒领靠两条规则兜底——
 * <ol>
 *   <li>令牌一律服务端随机生成，绝不接收客户端传入的令牌；</li>
 *   <li>已占用 userId（静态演示账号或已动态注册者）拒绝重复注册（40903）。</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserTokenRegistry userTokenRegistry;

    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = request.getUserId();
        Optional<String> tokenOpt = userTokenRegistry.register(userId);
        if (tokenOpt.isEmpty()) {
            throw new BusinessException(ResultCode.USER_ID_TAKEN,
                    "用户标识已被占用（静态演示账号或已注册），请更换其他用户 ID");
        }
        log.info("用户 {} 动态领取访问令牌成功", userId);
        return Result.success(new RegisterResponse(userId, tokenOpt.get()));
    }
}
