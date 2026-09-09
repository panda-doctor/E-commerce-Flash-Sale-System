package com.ghb.ecommerceflashsalesystem.integration;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 动态发令牌注册接口集成测试（R3 增强，Web 层 MockMvc）。
 *
 * <p>验证三件事：
 * <ol>
 *   <li>{@code /api/auth/register} 匿名可调（位于拦截路径之外），成功返回服务端随机令牌；</li>
 *   <li>领取的动态令牌能通过 {@code /api/seckill/**} 的 R3 用户鉴权拦截器
 *       （未领令牌请求被拒 40100，领令牌后放行至业务层）；</li>
 *   <li>防冒领与参数语义：静态账号 uid / 重复 uid 注册返回 40903，缺 userId 返回 40001。</li>
 * </ol>
 *
 * <p>动态 uid 由随机数生成（避开静态 1001~1006），重复执行不受内存注册表残留影响。
 */
@SpringBootTest(properties = "flash.stream.auto-poll=false")
@AutoConfigureMockMvc
public class AuthRegisterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** 生成一个本次运行专用的动态 uid（远离静态 1001~1006） */
    private long freshUid() {
        return 90000 + ThreadLocalRandom.current().nextInt(100000);
    }

    private String registerBody(long uid) {
        return "{\"userId\": " + uid + "}";
    }

    /** 注册成功并返回令牌（断言 code=0 且 token 为 32 位十六进制） */
    private String registerAndGetToken(long uid) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\"\\s*:\\s*\"([0-9a-f]{32})\".*", "$1");
        assertThat(token).hasSize(32);
        return token;
    }

    // 1. 注册成功：匿名可调，返回服务端随机令牌
    @Test
    void registerNewDynamicUserReturnsRandomToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(freshUid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(not(0)))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    // 2. 动态令牌可解锁受保护接口：无令牌被 R3 拦截器拒 40100，领取后放行到业务层
    @Test
    void dynamicTokenUnlocksProtectedExecuteEndpoint() throws Exception {
        long uid = freshUid();
        String token = registerAndGetToken(uid);
        String executeBody = "{\"activityId\": 999999, \"userId\": " + uid + "}";

        // 2.1 未领令牌：被 R3 拦截器拒绝
        mockMvc.perform(post("/api/seckill/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));

        // 2.2 带动态令牌：穿过拦截器进入业务（活动不存在返回业务码 + REJECTED，而不是鉴权错误）
        mockMvc.perform(post("/api/seckill/execute")
                        .header("X-User-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(ResultCode.UNAUTHORIZED.getCode())))
                .andExpect(jsonPath("$.data.result").value("REJECTED"));
    }

    // 3. 防冒领：静态演示账号 uid 不可动态注册
    @Test
    void staticAccountUidCannotBeRegistered() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.USER_ID_TAKEN.getCode()));
    }

    // 4. 防冒领：同一 uid 重复注册被拒
    @Test
    void duplicateRegisterOfSameUidRejected() throws Exception {
        long uid = freshUid();
        registerAndGetToken(uid); // 第一次注册成功

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.USER_ID_TAKEN.getCode()));
    }

    // 5. 参数缺失：userId 为空返回参数错误
    @Test
    void missingUserIdReturnsParamError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }
}
