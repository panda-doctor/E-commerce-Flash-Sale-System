package com.ghb.ecommerceflashsalesystem.service.ai;

import com.ghb.ecommerceflashsalesystem.domain.dto.request.AiChatRequest;
import com.ghb.ecommerceflashsalesystem.domain.vo.ChatReplyVO;

/**
 * AI 客服服务：把"商品/活动实时目录 + 系统人设 + 用户历史与提问"组装为大模型消息序列，
 * 委托 {@link LlmChatClient} 生成回复。
 */
public interface AiChatService {

    /**
     * 处理一次客服对话
     *
     * @param message 用户问题（非空，长度 ≤500）
     * @param history 最近对话上下文（时间正序，服务端最多取最近 10 条并单条截断）
     * @return 模型文本回复
     */
    ChatReplyVO chat(String message, java.util.List<AiChatRequest.ChatTurn> history);
}
