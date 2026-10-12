package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.AiChatRequest;
import com.ghb.ecommerceflashsalesystem.domain.vo.ChatReplyVO;
import com.ghb.ecommerceflashsalesystem.service.ai.AiChatService;
import com.ghb.ecommerceflashsalesystem.service.ai.LlmChatClient;
import com.ghb.ecommerceflashsalesystem.service.ai.OpenAiChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 客服对话链路集成测试
 *
 * <p>外部大模型不可在测试中真实调用，通过 {@link MockBean @MockBean} 替换
 * {@link LlmChatClient}（与生产实现 OpenAiCompatibleChatClient 同接口），
 * 验证 {@link AiChatService} 的组装逻辑：
 * 系统人设 + 实时商品/活动目录 + 用户历史清洗 + 当前问题 → OpenAI messages 序列 → 模型回复透传。
 *
 * <p>另验证参数护栏（空问题 / 超长问题 / 历史条数截断）。
 */
@SpringBootTest(properties = {
        "flash.stream.auto-poll=false",
        "ai.llm.api-key=sk-test-dummy",
        "ai.llm.base-url=https://api.example.com/v1",
        "ai.llm.model=dummy-model",
})
public class AiSupportChatIntegrationTest {

    @Autowired
    private AiChatService aiChatService;

    @MockBean
    private LlmChatClient chatClient;

    @Test
    void chatBuildsMessagesAndReturnsModelReply() {
        when(chatClient.chatCompletion(any())).thenReturn("当前正在秒杀机械键盘 Pro，快去抢～");

        AiChatRequest.ChatTurn turn1 = new AiChatRequest.ChatTurn();
        turn1.setRole("user");
        turn1.setContent("你好");
        AiChatRequest.ChatTurn turn2 = new AiChatRequest.ChatTurn();
        turn2.setRole("assistant");
        turn2.setContent("我在的，有什么可以帮你？");

        ChatReplyVO vo = aiChatService.chat("今天有什么秒杀？", List.of(turn1, turn2));

        // 1. 模型回复原样透传
        assertThat(vo.getReply()).isEqualTo("当前正在秒杀机械键盘 Pro，快去抢～");

        // 2. 发给模型的消息序列：system + 历史(user/assistant) + 当前 user
        ArgumentCaptor<List<OpenAiChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatClient, times(1)).chatCompletion(captor.capture());
        List<OpenAiChatMessage> sent = captor.getValue();
        assertThat(sent).hasSize(4);
        assertThat(sent.get(0).role()).isEqualTo("system");
        assertThat(sent.get(0).content())
                .contains("小闪")
                .contains("【当前在售商品】")
                .contains("秒杀");
        assertThat(sent.get(1).role()).isEqualTo("user");
        assertThat(sent.get(1).content()).isEqualTo("你好");
        assertThat(sent.get(2).role()).isEqualTo("assistant");
        assertThat(sent.get(3).role()).isEqualTo("user");
        assertThat(sent.get(3).content()).isEqualTo("今天有什么秒杀？");
    }

    @Test
    void historyIsNormalizedAndTrimmedToLatestTenTurns() {
        when(chatClient.chatCompletion(any())).thenReturn("ok");

        // 构造 15 轮历史，含应被丢弃的未知角色；服务端最多取最近 10 条
        List<AiChatRequest.ChatTurn> history = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            AiChatRequest.ChatTurn turn = new AiChatRequest.ChatTurn();
            turn.setRole(i % 2 == 0 ? "user" : "ai"); // ai 应归一化为 assistant
            turn.setContent("消息-" + i);
            history.add(turn);
        }
        AiChatRequest.ChatTurn bad = new AiChatRequest.ChatTurn();
        bad.setRole("system"); // 未知角色应被过滤
        bad.setContent("不要这条");
        history.add(bad);

        aiChatService.chat("再来一个", history);

        ArgumentCaptor<List<OpenAiChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatClient, times(1)).chatCompletion(captor.capture());
        List<OpenAiChatMessage> sent = captor.getValue();
        // system + 最近 10 条历史（15 轮中保留 turns[5..14]）+ 当前问题
        assertThat(sent).hasSize(12);
        assertThat(sent.get(1).content()).isEqualTo("消息-5");
        // 15 轮构造为 i 偶 user / i 奇 ai：turns[5] 为 ai → 应已归一化为 assistant
        assertThat(sent.get(1).role()).isEqualTo("assistant");
        assertThat(sent.get(2).content()).isEqualTo("消息-6");
        assertThat(sent.get(2).role()).isEqualTo("user");
        assertThat(sent.get(11).content()).isEqualTo("再来一个");
        assertThat(sent.get(11).role()).isEqualTo("user");
        // 未知角色/system 消息未进入序列
        assertThat(sent).noneMatch(m -> m.content().contains("不要这条"));
    }

    @Test
    void blankMessageRejectedAsParamError() {
        assertThatThrownBy(() -> aiChatService.chat("   ", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    assertThat(be.getMessage()).contains("不能为空");
                });
    }

    @Test
    void overlongMessageRejectedAsParamError() {
        String longMsg = "问".repeat(501);
        assertThatThrownBy(() -> aiChatService.chat(longMsg, List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                });
        verify(chatClient, times(0)).chatCompletion(any());
    }
}
