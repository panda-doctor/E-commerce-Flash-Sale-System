package com.ghb.ecommerceflashsalesystem.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 客服对话请求
 *
 * <p>无登录体系，服务端不存会话；由前端把最近几轮对话随请求带回（role/content 列表），
 * 后端组装成 OpenAI messages 序列后调用大模型，天然无状态、易水平扩展。
 *
 * <p>消息条数与单条长度在服务端有护栏，避免无谓的大模型 token 开销。
 */
@Data
public class AiChatRequest {

    /** 用户本次输入的问题 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题过长，最多 500 字")
    private String message;

    /** 可选的最近对话上下文（按时间正序），role 兼容 user / assistant / ai / model */
    private List<ChatTurn> history;

    @Data
    public static class ChatTurn {
        /** 消息角色：user / assistant（兼容前端可能传的 ai / model） */
        private String role;
        /** 消息文本 */
        private String content;
    }
}
