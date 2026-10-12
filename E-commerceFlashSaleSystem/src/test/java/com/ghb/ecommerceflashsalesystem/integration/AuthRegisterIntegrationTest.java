package com.ghb.ecommerceflashsalesystem.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"flash.stream.auto-poll=false", "security.admin-token=test-admin-token"})
@AutoConfigureMockMvc
class AuthRegisterIntegrationTest {
    private static final String PASSWORD = "CorrectHorse!123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void registrationCreatesPersistedAccountAndHttpOnlySession() throws Exception {
        MvcResult result = register(accountName());
        result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("FLASH_SESSION="))
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("FLASH_SESSION=")).findFirst().orElseThrow())
                .contains("HttpOnly").contains("SameSite=Strict");
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("passwordHash").doesNotContain(PASSWORD);
    }

    @Test
    void loginAndAuthenticatedSeckillUseSessionIdentityAndCsrf() throws Exception {
        String account = accountName();
        register(account);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountName\":\"" + account.toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        String csrf = body.at("/data/csrfToken").asText();
        Cookie sessionCookie = cookie(login, "FLASH_SESSION");
        Cookie csrfCookie = cookie(login, "FLASH_CSRF");

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie, csrfCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accountName").value(account));

        mockMvc.perform(post("/api/seckill/execute").cookie(sessionCookie, csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activityId\":999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(ResultCode.UNAUTHORIZED.getCode())))
                .andExpect(jsonPath("$.data.userId").isNotEmpty());

        mockMvc.perform(post("/api/seckill/execute").cookie(sessionCookie, csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activityId\":999999,\"userId\":123}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));

        mockMvc.perform(post("/api/seckill/execute").cookie(sessionCookie, csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activityId\":999999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ResultCode.CSRF_INVALID.getCode()));
    }

    @Test
    void duplicateAccountIsRejectedAndUnknownCredentialsAreGeneric() throws Exception {
        String account = accountName();
        register(account);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ResultCode.ACCOUNT_NAME_TAKEN.getCode()));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountName\":\"" + account + "\",\"password\":\"WrongPassword!123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ResultCode.INVALID_CREDENTIALS.getCode()));
    }

    @Test
    void adminCanCreateUpdateAndSoftDeleteUsers() throws Exception {
        String account = accountName();
        MvcResult created = mockMvc.perform(post("/api/admin/users").header("X-Admin-Token", "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(put("/api/admin/users/" + id).header("X-Admin-Token", "test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"新昵称\",\"status\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(delete("/api/admin/users/" + id).header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + id).header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    void oldAnonymousArbitraryIdRegistrationNoLongerCreatesIdentity() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":900001}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    private MvcResult register(String account) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
    }

    private String registerBody(String account) {
        return "{\"accountName\":\"" + account + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"顾客\"}";
    }

    private String accountName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    private Cookie cookie(MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow();
        return new Cookie(name, header.substring(name.length() + 1, header.indexOf(';')));
    }
}
