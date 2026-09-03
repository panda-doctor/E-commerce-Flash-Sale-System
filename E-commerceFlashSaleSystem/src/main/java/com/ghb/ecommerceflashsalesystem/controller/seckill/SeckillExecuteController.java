package com.ghb.ecommerceflashsalesystem.controller.seckill;


import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seckill")
public class SeckillExecuteController {
    private final SeckillService seckillService;

    @PostMapping("/execute")
    public Result<SeckillResponse> execute(@Valid @RequestBody SeckillRequest request) {
        SeckillResponse execute = seckillService.execute(request);
        log.info("秒杀执行成功，activityId={}, userId={}", request.getActivityId(), request.getUserId());
        return Result.success(execute);
    }
}
