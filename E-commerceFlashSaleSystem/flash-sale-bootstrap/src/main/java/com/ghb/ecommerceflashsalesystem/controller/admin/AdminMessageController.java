package com.ghb.ecommerceflashsalesystem.controller.admin;

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
 * 管理端死信补偿控制器（P3 Day 4：自 {@code AdminSeckillController} 拆出）。
 *
 * <p>死信重放属于 <b>order 域</b>（Stream 消费失败的补偿），不能随 activity 域迁入
 * {@code flash-sale-activity} 应用 —— 那会让活动服务反向依赖 order 模块，破坏域边界。
 * 因此原控制器一分为二、<b>HTTP 路径完全不变</b>：</p>
 * <ul>
 *   <li>建/改活动、预热、重置库存（activity 域）→ {@code flash-sale-activity} 应用（:8085）
 *       的 {@code activity.controller.AdminSeckillController}；</li>
 *   <li>{@code POST /api/admin/seckill/dead-letters/replay}（order 域）→ 本类（单体）。</li>
 * </ul>
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
