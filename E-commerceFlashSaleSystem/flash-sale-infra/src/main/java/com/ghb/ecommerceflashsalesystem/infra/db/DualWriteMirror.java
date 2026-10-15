package com.ghb.ecommerceflashsalesystem.infra.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P3 Day 3：过渡期「双写镜像」组件 —— 主库写成功后，把同一变更镜像到域独立 schema。
 *
 * <p><b>设计约束（四条，缺一不可）</b>：</p>
 * <ol>
 *   <li><b>默认关闭</b>：{@code module.db.dual-write.enabled=false}；未开启时本类所有方法**直接返回**（零开销）。</li>
 *   <li><b>只镜像写、不动读</b>：读路径仍指旧库 → 本日行为零变化。</li>
 *   <li><b>失败绝不影响主流程</b>：镜像异常只记 ERROR 日志 + 计数，主库已提交、不回滚；
 *       差量由对账脚本 {@code scripts/reconcile-product-activity.ps1} 兜底。</li>
 *   <li><b>调用点必须在主库事务提交之后</b>：避免"主库回滚但镜像已落"的不一致。</li>
 * </ol>
 *
 * <p>实现取舍：过渡期写量低，用一次性 JDBC 连接（不引入第二套连接池/多数据源路由），
 * 换取"零配置侵入 + 一眼看懂"；切换完成后本类连同开关一起删除。</p>
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

    /** 镜像 product 域写操作到 {@code flash_sale_product} */
    public void mirrorProduct(String sql, Object... params) {
        mirror("product", properties.getProductUrl(), sql, params);
    }

    /** 镜像 activity 域写操作到 {@code flash_sale_activity} */
    public void mirrorActivity(String sql, Object... params) {
        mirror("activity", properties.getActivityUrl(), sql, params);
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
                log.info("双写镜像成功，target={}, rows={}, cost={}ms", target, rows, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("双写镜像失败（不影响主流程，请跑对账脚本核对），target={}, failures={}, sql={}",
                    target, failureCount.get(), sql, e);
        }
    }
}
