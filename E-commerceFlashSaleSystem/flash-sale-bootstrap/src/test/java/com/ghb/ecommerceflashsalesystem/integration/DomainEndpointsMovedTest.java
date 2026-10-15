package com.ghb.ecommerceflashsalesystem.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void activityAdminEndpointsBelongToActivityService() throws Exception {
        // P3 Day 4：/api/admin/** 在单体被 ApiAccessWebConfig 的管理端拦截器**优先拒绝**
        // （无令牌恒为 200 + 40100）→ 该前缀不能用 404 判"接口是否搬走"，
        // 因此改用**类路径归属**做判据：HTTP 入口还在不在单体的 classpath 上。
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.activity.controller.AdminSeckillController"),
                "activity 的管理端接口（建/改活动、预热、重置库存）应已迁至 flash-sale-activity 应用（:8085）");
        // 反例：同一前缀下不属于 activity 的接口（order 域死信补偿）必须留在单体
        assertNotNull(Class.forName("com.ghb.ecommerceflashsalesystem.controller.admin.AdminMessageController"),
                "order 域的死信补偿接口应保留在单体（不能随 activity 域迁走）");
        // 行为面：无令牌访问该前缀仍被单体拦截器先拒 —— 与割接前行为一致
        mockMvc.perform(post("/api/admin/seckill/activities").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(0)));
    }

    @Test
    void seckillEndpointsBelongToSeckillService() throws Exception {
        // P4 Day 2：seckill 域的 4 个 HTTP 入口（活动列表/详情、可秒杀校验、执行）已迁至
        // flash-sale-seckill 应用（:8086）。与 activity 同理：/api/seckill/execute 在单体被
        // ApiAccessWebConfig 的用户态拦截器**优先拒绝**（恒 200 + 40100）→ 该前缀不能用
        // HTTP 状态判"接口是否搬走"，改用**类路径归属**（HTTP 入口还在不在单体 classpath 上）。
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.seckill.controller.SeckillExecuteController"),
                "秒杀执行入口应已迁至 flash-sale-seckill 应用（:8086）");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.seckill.controller.SeckillActivityController"),
                "秒杀活动读入口应已迁至 flash-sale-seckill 应用（:8086）");
        // 反例 1：seckill 的域服务必须留在库里 —— 单体仍以「本地装配」用它做库存写入
        assertNotNull(Class.forName("com.ghb.ecommerceflashsalesystem.seckill.service.SeckillStockService"),
                "seckill 库存写口（SeckillStockService）应在 flash-sale-seckill-core 库里，单体本地装配依赖它");
        // 反例 2：同一前缀下 order 域的接口必须留在单体（否则订单查询会被 seckill 应用抢走）
        assertNotNull(Class.forName("com.ghb.ecommerceflashsalesystem.order.controller.OrderController"),
                "order 域的订单查询接口应保留在单体（/api/seckill/orders/*、/api/seckill/users/*/orders 同前缀）");
        // 行为面：无令牌访问 execute 仍被单体拦截器先拒（实测 curl: 200 + 40100）—— 与割接前一致
        mockMvc.perform(post("/api/seckill/execute").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(0)));
        // 公开读路径不带拦截器：无处理器 → 404（可直接证明控制器已搬走）
        mockMvc.perform(get("/api/seckill/activities"))
                .andExpect(status().isNotFound());
    }
}
