package com.ghb.ecommerceflashsalesystem.controller.seckill;


import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.config.ApiAccessInterceptor;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seckill")
public class SeckillExecuteController {
    private final SeckillService seckillService;

    @PostMapping("/execute")
    public Result<SeckillResponse> execute(@Valid @RequestBody SeckillRequest request, HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute(ApiAccessInterceptor.AUTHENTICATED_USER_ID);
        if (authenticatedUserId == null || !authenticatedUserId.equals(request.getUserId())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请求用户与访问令牌不匹配");
        }
        try {
            SeckillResponse execute = seckillService.execute(request);
            log.info("秒杀执行成功，activityId={}, userId={}", request.getActivityId(), request.getUserId());
            return Result.success(execute);
        } catch (BusinessException e) {
            SeckillResponse response = new SeckillResponse();
            response.setActivityId(request.getActivityId());
            response.setUserId(request.getUserId());
            response.setResult("REJECTED");
            return Result.fail(e.getResultCode(), e.getMessage(), response);
        }
    }
}
