package com.ghb.ecommerceflashsalesystem.user.mirror;

import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserSession;
import com.ghb.ecommerceflashsalesystem.infra.db.DualWriteMirror;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * auth 域的「权威 → 副本」镜像写入器（P4 Day 6，过渡期组件，旧库表下线后本类一并删除）。
 *
 * <p><b>方向</b>：权威 = {@code flash_sale_auth}（**只有** auth 应用读写），
 * 副本 = 旧库 {@code flash_sale}（monolith / seckill / order 三个运行时的会话校验读它）。</p>
 *
 * <p><b>三条纪律</b>（沿用 {@code OrderMirror}）：</p>
 * <ol>
 *   <li><b>必须在权威库写入成功之后调用</b>；</li>
 *   <li><b>SQL 覆盖全部业务列</b>（{@code updateById} 的镜像也走同一条 upsert）；</li>
 *   <li><b>创建/更新时间戳用 {@code IFNULL(?, NOW())}</b>：MyBatis-Plus 跳过 null 字段 →
 *       实体里的 {@code createdAt} 常为 null（由 DB 默认值填），直接绑 null 会写坏副本。
 *       而 {@code deleted_at} / {@code revoked_at} 是**业务语义上的 null**（未删除/未吊销），
 *       必须原样透传，不能套 {@code IFNULL}。</li>
 * </ol>
 *
 * <p>⚠️ {@code user_session} 的**主键是 {@code token_hash}**（char(64)），没有自增 id ——
 * 它的 upsert 幂等键与对账指纹都以它为准。</p>
 *
 * <p>镜像默认关闭（{@code DualWriteProperties.enabled} 类内 false）→ 单体、集成测试零行为变化；
 * 只有 {@code flash-sale-auth} 应用的 yaml 显式开启。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthMirror {

    /** 用户账号 upsert（列顺序与 {@code user_account} 一致；副本表名同名，连接串已指向旧库） */
    private static final String UPSERT_ACCOUNT = """
            INSERT INTO user_account (id, account_name, password_hash, display_name, status,
                                      created_at, updated_at, deleted_at)
            VALUES (?, ?, ?, ?, ?, IFNULL(?, NOW()), IFNULL(?, NOW()), ?)
            ON DUPLICATE KEY UPDATE account_name = VALUES(account_name),
                                    password_hash = VALUES(password_hash),
                                    display_name = VALUES(display_name), status = VALUES(status),
                                    updated_at = VALUES(updated_at), deleted_at = VALUES(deleted_at)
            """;

    /** 会话 upsert（新建 / 轮换 CSRF / 吊销 三种写共用；主键 token_hash） */
    private static final String UPSERT_SESSION = """
            INSERT INTO user_session (token_hash, user_id, csrf_token_hash, expires_at, revoked_at, created_at)
            VALUES (?, ?, ?, ?, ?, IFNULL(?, NOW()))
            ON DUPLICATE KEY UPDATE csrf_token_hash = VALUES(csrf_token_hash),
                                    expires_at = VALUES(expires_at), revoked_at = VALUES(revoked_at)
            """;

    /** 单会话吊销（与权威库同语句形状） */
    private static final String REVOKE_SESSION =
            "UPDATE user_session SET revoked_at = ? WHERE token_hash = ?";

    /** CSRF 轮换（守卫与权威库 `UserSessionMapper.rotateCsrf` 一致：不把已失效会话在副本上写活） */
    private static final String ROTATE_CSRF = """
            UPDATE user_session SET csrf_token_hash = ?
            WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
            """;

    /** 按用户批量吊销（改密 / 禁用 / 软删时调用） */
    private static final String REVOKE_ALL_FOR_USER =
            "UPDATE user_session SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL";

    /** 过期/已吊销会话清理（副本侧：与权威库 `deleteExpired` 同一条 DELETE，由 auth 应用的清理调度器触发） */
    private static final String PURGE_EXPIRED =
            "DELETE FROM user_session WHERE expires_at < ? OR revoked_at < ?";

    private final DualWriteMirror mirror;

    /** 账号创建 / 资料变更 / 状态变更 / 软删后镜像 */
    public void upsertAccount(UserAccount user) {
        if (user == null) {
            return;
        }
        mirror.mirrorAuth(UPSERT_ACCOUNT,
                user.getId(), user.getAccountName(), user.getPasswordHash(), user.getDisplayName(),
                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt(), user.getDeletedAt());
    }

    /** 会话新建 / CSRF 轮换 / 吊销后镜像 */
    public void upsertSession(UserSession session) {
        if (session == null) {
            return;
        }
        mirror.mirrorAuth(UPSERT_SESSION,
                session.getTokenHash(), session.getUserId(), session.getCsrfTokenHash(),
                session.getExpiresAt(), session.getRevokedAt(), session.getCreatedAt());
    }

    /** 单会话吊销（登出） */
    public void revokeSession(String tokenHash, LocalDateTime now) {
        if (tokenHash == null) {
            return;
        }
        mirror.mirrorAuth(REVOKE_SESSION, now, tokenHash);
    }

    /** CSRF 轮换（`/api/auth/csrf` 会改这一列） */
    public void rotateCsrf(String tokenHash, String csrfTokenHash, LocalDateTime now) {
        if (tokenHash == null) {
            return;
        }
        mirror.mirrorAuth(ROTATE_CSRF, csrfTokenHash, tokenHash, now);
    }

    /** 按用户批量吊销（改密 / 禁用 / 软删）：副本侧执行同一条 set-based UPDATE（无需逐行回读） */
    public void revokeAllForUser(Long userId, LocalDateTime now) {
        if (userId == null) {
            return;
        }
        mirror.mirrorAuth(REVOKE_ALL_FOR_USER, now, userId);
    }

    /**
     * 副本侧清理过期会话。
     *
     * <p>权威在 {@code flash_sale_auth}，副本的过期行若不一起清会长期堆积（且对账的
     * `auth.session_rows` 会漂移）→ 由 auth 应用的清理调度器两侧都清。</p>
     */
    public void purgeExpired(LocalDateTime cutoff) {
        mirror.mirrorAuth(PURGE_EXPIRED, cutoff, cutoff);
    }
}
