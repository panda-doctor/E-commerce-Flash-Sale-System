package com.ghb.ecommerceflashsalesystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 客服大模型配置（OpenAI 兼容协议，配置前缀 ai.llm）
 *
 * <p>支持任意 OpenAI Chat Completions 兼容服务：
 * <ul>
 *   <li>阿里云百炼（通义千问）兼容模式：{@code https://dashscope.aliyuncs.com/compatible-mode/v1}</li>
 *   <li>DeepSeek：{@code https://api.deepseek.com/v1}</li>
 *   <li>OpenAI：{@code https://api.openai.com/v1}</li>
 * </ul>
 * base-url 给到服务根即可（可含 /v1），Client 会自动拼接 {@code /chat/completions}（若已给到
 * {@code .../chat/completions} 也能识别）。
 *
 * <p>api-key 支持通过环境变量注入（如 {@code AI_LLM_API_KEY=sk-xxx}），避免明文入库：
 * yaml 中写 {@code api-key: ${AI_LLM_API_KEY:}}。留空时启动不受影响，调用侧返回 50300 可读错误。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.llm")
public class AiLlmProperties {

    /** OpenAI 兼容服务根地址（示例见类注释），必填才能发起对话 */
    private String baseUrl = "https://api.deepseek.com";

    /** 调用密钥（Bearer），留空则对话接口返回 AI_SERVICE_UNCONFIGURED */
    private String apiKey = "sk-f0a743f6f4184ec28672d0daa9727107";

    /** 模型名，如 qwen-plus / deepseek-chat / gpt-4o-mini */
    private String model = "deepseek-v4-flash";

    /** 采样温度 0~2，默认 0.7（商城问答偏保守可调低） */
    private double temperature = 0.7;

    /** 单次回复最大 token 数 */
    private int maxTokens = 800;

    /** HTTP 连接/读取超时（秒） */
    private int timeoutSeconds = 30;
}
