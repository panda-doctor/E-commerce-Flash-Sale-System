package com.ghb.ecommerceflashsalesystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.llm")
public class AiLlmProperties {

    /** OpenAI 兼容服务根地址（示例见类注释），必填才能发起对话 */
    private String baseUrl = "https://api.deepseek.com";

    /** 调用密钥（Bearer），留空则对话接口返回 AI_SERVICE_UNCONFIGURED */
    private String apiKey = "";

    /** 模型名，如 qwen-plus / deepseek-chat / gpt-4o-mini */
    private String model = "deepseek-chat";

    /** 采样温度 0~2，默认 0.7（商城问答偏保守可调低） */
    private double temperature = 0.7;

    /** 单次回复最大 token 数 */
    private int maxTokens = 800;

    /** HTTP 连接/读取超时（秒） */
    private int timeoutSeconds = 30;
}
