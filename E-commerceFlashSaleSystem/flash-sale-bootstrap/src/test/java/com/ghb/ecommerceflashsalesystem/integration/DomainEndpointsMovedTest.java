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
        // 反例（P4 Day 3 后含义反转）：order 域的死信补偿接口曾在单体暂居（P3 Day 4 拆出时），
        // order 服务化后随域迁到 flash-sale-order 应用 —— 入口归属用**文件位置**表达，
        // 单体不再持有任何 order 域 HTTP 入口
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.controller.admin.AdminMessageController"),
                "order 域的死信补偿接口应已迁至 flash-sale-order 应用（:8087）");
        // 行为面：无令牌访问该前缀仍被单体拦截器先拒 —— 与割接前行为一致
        mockMvc.perform(post("/api/admin/seckill/activities").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(0)));
    }

    /**
     * P4 Day 6：过期会话清理的**调度入口**归属 auth 应用。
     *
     * <p>清理逻辑 {@code UserSessionService.purgeExpiredSessions()} 在 auth-core 共享库里，
     * 任何"带 {@code @EnableScheduling} 且宿主 auth-core"的运行时都会触发它 —— 单体正是这样的
     * 运行时（连同 order / seckill 一共三个）。auth 权威切到 {@code flash_sale_auth} 之后，
     * 若单体继续跑就成了"非属主去删副本"：因此调度类必须**只在** auth 应用。
     * 本断言与 Day 3 的 {@code StreamConsumerScheduler} 守卫同形。</p>
     */
    @Test
    void authSessionCleanupSchedulerBelongsToAuthService() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.user.scheduler.AuthSessionCleanupScheduler"));
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
        // 反例 2（P4 Day 3 后含义反转）：order 域的订单查询入口已随 order 服务化迁走，
        // 但**消费者本体**必须留在库里（4 个集成测试注入它、由测试自行驱动消费）——
        // 迁走的是"入口"，不是"域"
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.order.controller.OrderController"),
                "order 的订单查询入口应已迁至 flash-sale-order 应用（:8087）");
        assertNotNull(Class.forName("com.ghb.ecommerceflashsalesystem.order.consumer.SeckillOrderConsumer"),
                "Stream 消费者本体应留在 flash-sale-order-core（集成测试注入它驱动消费）");
        // 行为面：无令牌访问 execute 仍被单体拦截器先拒（实测 curl: 200 + 40100）—— 与割接前一致
        mockMvc.perform(post("/api/seckill/execute").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(0)));
        // 公开读路径不带拦截器：无处理器 → 404（可直接证明控制器已搬走）
        mockMvc.perform(get("/api/seckill/activities"))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderEndpointsAndSchedulerBelongToOrderService() throws Exception {
        // P4 Day 3：order 域服务化（flash-sale-order :8087）。
        // **本日唯一的高危点是重复消费**：消费者本体故意留在 order-core（4 个集成测试注入它），
        // 所以必须钉住"调度入口只在应用一侧" —— 一旦调度器类回到单体 classpath，
        // 加上单体的 @EnableScheduling 就会双份建单。
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.order.scheduler.StreamConsumerScheduler"),
                "Stream 消费调度器必须只存在于 flash-sale-order 应用（否则单体与应用会重复建单）");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ghb.ecommerceflashsalesystem.order.controller.AdminMessageController"),
                "死信回放入口应已迁至 flash-sale-order 应用（:8087）");
        // 域服务留在库里：单体仍持有它们（集成测试也注入它们驱动消费）
        assertNotNull(Class.forName("com.ghb.ecommerceflashsalesystem.order.service.OrderQueryService"),
                "订单查询服务应留在 flash-sale-order-core");
        assertNotNull(Class.forName("com.ghb.ecommerceflashsalesystem.order.service.DeadLetterReplayService"),
                "死信回放服务应留在 flash-sale-order-core");
        // 行为面：三条订单查询路径都在 auth-core 的用户态拦截器名单里 → 无令牌恒 200 + 40100
        //（与 /api/admin/** 同理，不能靠 HTTP 404 判"接口是否搬走"）
        mockMvc.perform(get("/api/seckill/orders/SK-PROBE-NOT-EXIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(0)));
        mockMvc.perform(get("/api/seckill/users/me/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(0)));
    }
}
