package com.ghb.ecommerceflashsalesystem.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.config.AiLlmProperties;
import com.ghb.ecommerceflashsalesystem.service.ai.OpenAiChatMessage;
import com.ghb.ecommerceflashsalesystem.service.ai.OpenAiCompatibleChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 兼容客户端「未配置」场景单元测试
 *
 * <p>纯单元（不启动 Spring）：验证缺 api-key / 缺 base-url 时在发起任何网络请求前
 * 抛出 50300 可读错误，防止拿占位/空配置去外呼大模型。
 */
public class AiClientUnconfiguredTest {

    @Test
    void blankApiKeyThrowsUnconfiguredBeforeNetwork() {
        AiLlmProperties props = new AiLlmProperties();
        props.setApiKey("   "); // 空白视为未配置
        props.setBaseUrl("https://api.example.com/v1");
        OpenAiCompatibleChatClient client = buildClient(props);

        assertThatThrownBy(() -> client.chatCompletion(List.of(new OpenAiChatMessage("user", "hi"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.AI_SERVICE_UNCONFIGURED.getCode());
                    assertThat(be.getMessage()).contains("api-key");
                });
    }

    @Test
    void blankBaseUrlThrowsUnconfiguredBeforeNetwork() {
        AiLlmProperties props = new AiLlmProperties();
        props.setApiKey("sk-xxx");
        props.setBaseUrl(" "); // 空白视为未配置
        OpenAiCompatibleChatClient client = buildClient(props);

        assertThatThrownBy(() -> client.chatCompletion(List.of(new OpenAiChatMessage("user", "hi"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.AI_SERVICE_UNCONFIGURED.getCode());
                    assertThat(be.getMessage()).contains("base-url");
                });
    }

    private OpenAiCompatibleChatClient buildClient(AiLlmProperties props) {
        return new OpenAiCompatibleChatClient(props, RestClient.builder(), new ObjectMapper());
    }
}
