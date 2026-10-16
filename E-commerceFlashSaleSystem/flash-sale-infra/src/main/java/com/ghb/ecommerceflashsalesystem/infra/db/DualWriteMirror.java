package com.ghb.ecommerceflashsalesystem.infra.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 过渡期「域库镜像」组件 —— 权威库写成功后，把同一变更镜像到**副本库**。
 *
 * <p><b>方向已随 P4 Day 1「切读」反转</b>（Day 3 写这份类时是"旧库 → 新库"，现在相反）：</p>
 * <ul>
 *   <li>权威库 = 新 schema（{@code flash_sale_product} / {@code flash_sale_activity}），
 *       两个已拆应用读写都在这里；</li>
 *   <li>副本库 = 旧库 {@code flash_sale}，供**未拆模块**（seckill / ai / analytics，仍在单体）读 ——
 *       它们的数据新鲜度完全依赖本镜像。</li>
 * </ul>
 *
 * <p><b>设计约束（四条，缺一不可）</b>：</p>
 * <ol>
 *   <li><b>类内默认关闭</b>：{@code module.db.dual-write.enabled} 默认 false（安全默认值，防止没配
 *       的进程误写第二个库）；**两个已拆应用的 yaml 显式默认开启**，因为它们是副本的维护者。</li>
 *   <li><b>只镜像写、不动权威读</b>：读路径一律走权威库；副本只服务未拆模块。</li>
 *   <li><b>失败绝不影响主流程</b>：镜像异常只记 ERROR 日志 + 计数，权威库已提交、不回滚；
 *       差量由对账脚本 {@code scripts/reconcile-product-activity.ps1} 兜底（切换期它同时是门禁）。</li>
 *   <li><b>调用点必须在权威库事务提交之后</b>：避免"权威库回滚但副本已落"的不一致。</li>
 * </ol>
 *
 * <p>实现取舍：过渡期写量低，用一次性 JDBC 连接（不引入第二套连接池/多数据源路由），
 * 换取"零配置侵入 + 一眼看懂"；等未拆模块全部迁成服务、旧库表下线后，本类连同开关一起删除。</p>
 *
 * <p>本类刻意**不依赖任何业务实体**（infra 不能反向依赖业务模块），
 * 由调用方传入 SQL 与参数，镜像"到底写了什么"在调用点一目了然。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualWriteMirror {

    /** 镜像库连接超时（秒）：防止镜像库不可达时拖住主流程 */
    private static final int LOGIN_TIMEOUT_SECONDS = 3;

    private final DualWriteProperties properties;

    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();

    /** 开关是否打开（调用方可据此跳过 SQL 拼装） */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 镜像成功次数（观测用） */
    public long successCount() {
        return successCount.get();
    }

    /** 镜像失败次数（观测用；>0 说明需要跑对账脚本） */
    public long failureCount() {
        return failureCount.get();
    }

    /** 把 product 域写操作镜像到**副本库**（旧库 {@code flash_sale}，供未拆模块读） */
    public void mirrorProduct(String sql, Object... params) {
        mirror("product", properties.getLegacyProductUrl(), sql, params);
    }

    /** 把 activity 域写操作镜像到**副本库**（旧库 {@code flash_sale}，供未拆模块读） */
    public void mirrorActivity(String sql, Object... params) {
        mirror("activity", properties.getLegacyActivityUrl(), sql, params);
    }

    /**
     * 把 order 域写操作（订单 / 消息日志）镜像到**副本库**（旧库 {@code flash_sale}）。
     *
     * <p>副本的读者是**单体侧的 analytics**（`OrderQueryAdapter` 经 `SeckillOrderMapper` 读
     * {@code seckill_order} 给榜单/指标补订单号）—— 它还没服务化，所以 order 也必须走
     * 「权威 → 副本」这条反向镜像（P4 Day 4，与 product/activity 同法）。</p>
     */
    public void mirrorOrder(String sql, Object... params) {
        mirror("order", properties.getLegacyOrderUrl(), sql, params);
    }

    /**
     * 把 auth 域写操作（用户账号 / 会话）镜像到**副本库**（旧库 {@code flash_sale}）。
     *
     * <p>副本的读者是 **monolith / seckill / order 三个运行时**里的会话校验
     * （`ApiAccessInterceptor` → `UserSessionService.authenticate` 读 {@code user_session}、
     * `UserAccountService.getActive` 读 {@code user_account}）—— auth 域尚未服务化，
     * 它们只能读旧库，所以 auth 也必须走「权威 → 副本」反向镜像（P4 Day 6，与 product/activity/order 同法）。</p>
     */
    public void mirrorAuth(String sql, Object... params) {
        mirror("auth", properties.getLegacyAuthUrl(), sql, params);
    }

    private void mirror(String target, String url, String sql, Object... params) {
        if (!properties.isEnabled()) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
            try (Connection conn = DriverManager.getConnection(url, properties.getUsername(), properties.getPassword());
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                int rows = ps.executeUpdate();
                successCount.incrementAndGet();
                // 日志带目标库名：切读期"镜像方向"是最容易搞反的地方，让它一眼可辨
                log.info("镜像成功（权威 -> 副本），target={}(db={}), rows={}, cost={}ms",
                        target, databaseOf(url), rows, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("镜像失败（不影响主流程，请跑对账脚本核对），target={}(db={}), failures={}, sql={}",
                    target, databaseOf(url), failureCount.get(), sql, e);
        }
    }

    /** 从 JDBC URL 里取库名（仅用于日志；解析失败返回 {@code ?}） */
    static String databaseOf(String url) {
        if (url == null) {
            return "?";
        }
        int hostEnd = url.indexOf("//");
        int slash = hostEnd < 0 ? -1 : url.indexOf('/', hostEnd + 2);
        if (slash < 0) {
            return "?";
        }
        int queryStart = url.indexOf('?', slash);
        return queryStart < 0 ? url.substring(slash + 1) : url.substring(slash + 1, queryStart);
    }
}
