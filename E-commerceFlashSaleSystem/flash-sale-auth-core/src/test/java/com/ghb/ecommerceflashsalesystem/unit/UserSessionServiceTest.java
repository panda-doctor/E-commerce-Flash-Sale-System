package com.ghb.ecommerceflashsalesystem.unit;

import com.ghb.ecommerceflashsalesystem.user.config.SecurityProperties;
import com.ghb.ecommerceflashsalesystem.user.mapper.UserSessionMapper;
import com.ghb.ecommerceflashsalesystem.user.mirror.AuthMirror;
import com.ghb.ecommerceflashsalesystem.user.service.UserAccountService;
import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 Day 6：会话清理的两条契约（这两条正是本日"顺带修潜伏 bug"的核心）。
 *
 * <ol>
 *   <li><b>两侧都清</b>：{@code purgeExpiredSessions()} 既要删权威库（{@code flash_sale_auth}），
 *       也要镜像删副本（旧库）—— 否则旧库过期行长期堆积、对账的 auth 检查会漂移；</li>
 *   <li><b>不自触发</b>：{@code purgeExpiredSessions()} **不得带 {@code @Scheduled}**。
 *       这个类活在 auth-core 共享库里，而"宿主 auth-core 且开了 {@code @EnableScheduling}"
 *       的运行时一共三个（单体 / order / seckill）—— 注解留在库里等于三方各自删一次；
 *       权威切到 flash_sale_auth 之后，另外两个就成了"非属主删副本"。</li>
 * </ol>
 */
class UserSessionServiceTest {

    private UserSessionMapper sessionMapper;
    private AuthMirror authMirror;
    private UserSessionService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(UserSessionMapper.class);
        authMirror = mock(AuthMirror.class);
        service = new UserSessionService(sessionMapper, mock(UserAccountService.class),
                new SecurityProperties(), authMirror);
    }

    @Test
    void purgeExpiredSessionsClearsAuthorityAndMirrorsReplica() {
        when(sessionMapper.deleteExpired(any(LocalDateTime.class))).thenReturn(3);

        int deleted = service.purgeExpiredSessions();

        assertThat(deleted).isEqualTo(3);
        verify(sessionMapper).deleteExpired(any(LocalDateTime.class));
        verify(authMirror).purgeExpired(any(LocalDateTime.class));
    }

    @Test
    void purgeExpiredSessionsIsNotSelfScheduled() throws Exception {
        Method method = UserSessionService.class.getMethod("purgeExpiredSessions");

        assertThat(method.getAnnotations())
                .as("调度入口必须只在 flash-sale-auth 的 AuthSessionCleanupScheduler 上")
                .noneMatch(annotation -> isSchedulingAnnotation(annotation));
    }

    private static boolean isSchedulingAnnotation(Annotation annotation) {
        return annotation.annotationType().getName().endsWith("Scheduled");
    }
}
