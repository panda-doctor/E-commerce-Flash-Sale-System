package com.ghb.ecommerceflashsalesystem.ai.controller;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.ai.dto.request.AiChatRequest;
import com.ghb.ecommerceflashsalesystem.ai.vo.ChatReplyVO;
import com.ghb.ecommerceflashsalesystem.ai.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 客服接口
 *
 * POST /api/support/chat
 * 请求：{ "message": "...", "history": [{ "role": "user|assistant", "content": "..." }] }
 * 响应：data = { "reply": "模型回复文本" }
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class AiSupportController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public Result<ChatReplyVO> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiChatService.chat(request.getMessage(), request.getHistory()));
    }
}
