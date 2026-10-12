package com.ghb.ecommerceflashsalesystem.service.ai;

import java.util.List;

/**
 * OpenAI 兼容 Chat Completions 客户端。
 *
 * <p>抽象为接口便于测试：集成测试通过 {@code @MockBean} 桩住实现，不发起真实外部请求。
 * 当前唯一实现为 {@link OpenAiCompatibleChatClient}（HTTP 调用外部大模型）。
 */
public interface LlmChatClient {

    /**
     * 发起一次多轮对话补全（messages 由调用方组装好 system/user/assistant 完整序列）
     *
     * @param messages OpenAI 消息列表（至少含 1 条，一般首条为 system）
     * @return 模型回复文本
     * @throws com.ghb.ecommerceflashsalesystem.common.exception.BusinessException
     *         50300 未配置密钥；50301 网络/超时/非 2xx/响应结构异常
     */
    String chatCompletion(List<OpenAiChatMessage> messages);
}
