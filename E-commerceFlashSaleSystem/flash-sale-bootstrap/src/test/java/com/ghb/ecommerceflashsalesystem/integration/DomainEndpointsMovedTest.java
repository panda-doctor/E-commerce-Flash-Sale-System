package com.ghb.ecommerceflashsalesystem.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 域拆分后的「接口归属」守卫测试（Day 2 拆 auth、Day 3 拆 ai）。
 *
 * <p>单体不应再暴露已拆出域的接口。这里用 <b>POST</b> 断言 404：
 * POST 在无处理器时不会被 MVC 拦截器"提前拒绝"（资源处理器只支持 GET/HEAD），
 * 所以 404 是"处理器确实不存在"的可靠判据；若用 GET，可能因拦截器命中而返回 200 + 40100，
 * 无法区分"接口没了"与"没登录"。</p>
 */
@SpringBootTest(properties = {"flash.stream.auto-poll=false", "security.admin-token=test-admin-token"})
@AutoConfigureMockMvc
class DomainEndpointsMovedTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void authEndpointsBelongToAuthService() throws Exception {
        // 未被 MVC 拦截器覆盖的路径：POST 无处理器 → 404，可直接证明接口已不在单体
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        // 被 ApiAccessWebConfig 覆盖的路径（logout）：拦截器先拒绝 → 200 + 非 0 业务码；
        // 单体已无 auth 处理器，业务成功码 0 永远拿不到（与 curl 实测一致）
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(0)));
    }

    @Test
    void aiEndpointBelongsToAiService() throws Exception {
        mockMvc.perform(post("/api/support/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void productEndpointsBelongToProductService() throws Exception {
        // ProductController 已迁至 flash-sale-product：POST /api/products 在单体已无处理器 → 404。
        // ⚠️ 不要用 /api/admin/** 做探针：那些路径被 ApiAccessWebConfig 管理端拦截器先拒绝，
        //    永远返回 200 + 40100，无法区分「接口是否搬走」。
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        // 静态资源出口 /uploads/** 随 FileStorageWebConfig 一并迁出 → 404（公开路径，不受拦截器影响）
        mockMvc.perform(get("/uploads/probe.jpg"))
                .andExpect(status().isNotFound());
        // GET /api/products/{id} 同属公开路径：无处理器 → 404（比 POST 更贴近真实调用形态）
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isNotFound());
    }
}
