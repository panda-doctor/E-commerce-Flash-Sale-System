package com.ghb.ecommerceflashsalesystem.order.controller;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.order.dto.request.DeadLetterReplayRequest;
import com.ghb.ecommerceflashsalesystem.order.service.DeadLetterReplayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端死信补偿控制器（P3 Day 4 自 {@code AdminSeckillController} 拆出；<b>P4 Day 3 随 order 域迁入本应用</b>）。
 *
 * <p>死信重放属于 <b>order 域</b>（Stream 消费失败的补偿），HTTP 路径不变：
 * {@code POST /api/admin/seckill/dead-letters/replay}。P3 Day 4 时它暂居单体
 * （bootstrap 的 {@code controller.admin}），order 服务化后按"入口归属用文件位置表达"的原则
 * 迁到这里 —— 单体不再持有任何 order 域的 HTTP 入口。</p>
 *
 * <p>安全：{@code /api/admin/**} 由 infra 的 {@code AdminAccessWebConfig} 校验
 * {@code X-Admin-Token}（与 auth-core 同 key、fail-closed）。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/seckill")
public class AdminMessageController {

    private final DeadLetterReplayService deadLetterReplayService;

    /** POST /api/admin/seckill/dead-letters/replay：人工重新投递一条已补偿的死信。 */
    @PostMapping("/dead-letters/replay")
    public Result<Map<String, String>> replayDeadLetter(@Valid @RequestBody DeadLetterReplayRequest request) {
        String replayMessageId = deadLetterReplayService.replay(request.getOriginalMessageId());
        return Result.success(Map.of(
                "originalMessageId", request.getOriginalMessageId(),
                "replayMessageId", replayMessageId));
    }
}
