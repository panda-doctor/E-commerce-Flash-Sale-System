package com.ghb.ecommerceflashsalesystem.service.ai;

/**
 * OpenAI Chat Completions 协议中的一条消息。
 *
 * <p>role 取值：system（系统设定）/ user（用户）/ assistant（模型回复）。
 * 以 record 承载、直接交由 Jackson 序列化为请求体消息项，与 Spring Data Redis
 * 无关，仅作外部 HTTP 请求模型。
 *
 * @param role   消息角色
 * @param content 文本内容
 */
public record OpenAiChatMessage(String role, String content) {
}
