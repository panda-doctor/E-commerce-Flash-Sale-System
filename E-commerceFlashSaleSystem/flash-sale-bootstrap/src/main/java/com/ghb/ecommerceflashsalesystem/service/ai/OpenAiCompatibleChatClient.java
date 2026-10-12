package com.ghb.ecommerceflashsalesystem.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.config.AiLlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 的 HTTP 实现（默认实现）。
 *
 * <ul>
 *   <li>协议：POST {@code {base-url}/chat/completions}，Header {@code Authorization: Bearer {api-key}}</li>
 *   <li>请求体：{@code {model, messages, temperature, max_tokens, stream=false}}</li>
 *   <li>响应：取 {@code choices[0].message.content} 纯文本；任何非 2xx / IO / 解析异常统一转
 *       {@link ResultCode#AI_SERVICE_ERROR}，不让底层异常裸抛到全局（避免把 key 或内网信息带出）。</li>
 *   <li>密钥未配置（api-key 为空）时直接抛 {@link ResultCode#AI_SERVICE_UNCONFIGURED}，不发网络请求。</li>
 *   <li><b>空回复重试</b>：部分模型对复杂场景（如"当前无进行中场次"）偶发返回空 content，
 *       会自动以相同消息重发 1 次；仍为空则抛 {@link ResultCode#AI_SERVICE_ERROR}（可读提示，不把空串当成功）。</li>
 * </ul>
 */
@Slf4j
@Service
public class OpenAiCompatibleChatClient implements LlmChatClient {

    /** 空回复最多重试次数（含首次共发起 {@code MAX_EMPTY_RETRY + 1} 次请求） */
    private static final int MAX_EMPTY_RETRY = 1;

    private final AiLlmProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    private RestClient restClient;

    public OpenAiCompatibleChatClient(AiLlmProperties properties,
                                      RestClient.Builder restClientBuilder,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chatCompletion(List<OpenAiChatMessage> messages) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNCONFIGURED,
                    "AI 服务未配置：请在 application.yaml 设置 ai.llm.api-key（支持环境变量 AI_LLM_API_KEY）");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNCONFIGURED,
                    "AI 服务未配置：请在 application.yaml 设置 ai.llm.base-url（OpenAI 兼容服务根地址）");
        }
        ensureRestClient();

        for (int attempt = 0; attempt <= MAX_EMPTY_RETRY; attempt++) {
            String content = requestOnce(messages, apiKey);
            if (content != null && !content.trim().isEmpty()) {
                log.info("AI 对话完成，model={}, inputMessages={}, outputChars={}",
                        properties.getModel(), messages.size(), content.trim().length());
                return content.trim();
            }
            log.warn("AI 返回空内容（第 {} 次尝试），将重试", attempt + 1);
        }
        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 暂时没有生成内容，请再问一次");
    }

    /** 单次补全请求：返回 choices[0].message.content（可能为空串），异常统一转 50301 */
    private String requestOnce(List<OpenAiChatMessage> messages, String apiKey) {
        String uri = chatCompletionsUri();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("stream", false);

        try {
            String raw = restClient.post()
                    .uri(uri)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String err = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                                "大模型接口返回 HTTP " + response.getStatusCode().value()
                                        + "：" + abbreviate(err, 200));
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode choice = root.path("choices").isArray() && root.path("choices").size() > 0
                    ? root.path("choices").get(0)
                    : null;
            if (choice == null || choice.path("message").path("content").isMissingNode()) {
                log.warn("大模型响应缺少 choices[0].message.content，raw={}", abbreviate(raw, 300));
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "大模型响应格式异常，请稍后重试");
            }
            return choice.path("message").path("content").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用大模型失败，uri={}, model={}", uri, properties.getModel(), e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 懒构建 RestClient（首次真实调用前初始化；可并发重复执行，幂等）。
     * 拆出独立方法而非 @PostConstruct，便于单元测试直接 new 客户端而不依赖 Spring 容器。
     */
    private void ensureRestClient() {
        if (this.restClient != null) {
            return;
        }
        synchronized (this) {
            if (this.restClient == null) {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                int timeoutMs = properties.getTimeoutSeconds() * 1000;
                factory.setConnectTimeout(timeoutMs);
                factory.setReadTimeout(timeoutMs);
                this.restClient = restClientBuilder.requestFactory(factory).build();
            }
        }
    }

    /** 容忍配置末尾带不带 /chat/completions，统一拼出完整补全地址 */
    private String chatCompletionsUri() {
        String base = properties.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
