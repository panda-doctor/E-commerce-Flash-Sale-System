package com.ghb.ecommerceflashsalesystem.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部接口安全断言（P3 Day 5）。
 *
 * <p>{@code /internal/**} 是服务间调用入口，<b>不经网关</b>（网关的准入对它无效），
 * 所以必须自带令牌校验（infra 的 {@code InternalAccessWebConfig}），且语义是
 * <b>fail-closed</b>：令牌缺失/不匹配一律 40100。</p>
 *
 * <p>为什么要写 @SpringBootTest 级别的断言：这是"安全默认值"的回归网 ——
 * 将来谁把 {@code /internal/**} 的拦截器摘掉、或把前缀改成别的形式，
 * 这里会立刻变红（而不是等到某天发现库存写口裸奔）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "module.internal.token=test-internal-token")
class InternalEndpointSecurityTest {

    private static final String STOCK_PATH = "/internal/seckill/activities/1/stock";
    private static final String BODY = "{\"stock\":10,\"ttlSeconds\":600}";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Autowired
    private MockMvc mockMvc;

    /** 不带令牌：拒绝（fail-closed），不得进入业务逻辑 */
    @Test
    void stockEndpoint_withoutToken_isRejected() throws Exception {
        mockMvc.perform(post(STOCK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /** 令牌不匹配：拒绝 */
    @Test
    void stockEndpoint_withWrongToken_isRejected() throws Exception {
        mockMvc.perform(post(STOCK_PATH)
                        .header(INTERNAL_TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /** 令牌正确：通过校验（后续写 Redis 是否可用与本断言无关，只要求"不是被安全层拒绝"） */
    @Test
    void stockEndpoint_withCorrectToken_passesGuard() throws Exception {
        mockMvc.perform(post(STOCK_PATH)
                        .header(INTERNAL_TOKEN_HEADER, "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(40100)));
    }
}
