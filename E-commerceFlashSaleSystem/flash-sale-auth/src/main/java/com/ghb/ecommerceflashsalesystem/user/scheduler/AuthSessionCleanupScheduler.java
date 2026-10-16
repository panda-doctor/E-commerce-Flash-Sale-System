package com.ghb.ecommerceflashsalesystem.user.scheduler;

import com.ghb.ecommerceflashsalesystem.user.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * auth 域的过期会话清理**调度入口**（P4 Day 6）。
 *
 * <p><b>为什么这个类在应用里、而不在 auth-core 库里</b>：清理逻辑
 * {@link UserSessionService#purgeExpiredSessions()} 是库代码（可测、可被其它流程调用），
 * 但"谁来定时触发"是**部署事实** —— 带 {@code @EnableScheduling} 且宿主 auth-core 的运行时
 * 一共三个（monolith / order / seckill），把 {@code @Scheduled} 留在库里等于让**三个进程
 * 每天各自 DELETE 一次**：拆分前都指旧库、同表幂等所以无害；auth 权威切到
 * {@code flash_sale_auth} 之后，另外两个就会变成"非属主去删副本"。
 * 归属用**文件位置**表达，与 P4 Day 3 的 {@code StreamConsumerScheduler} 同一手法。</p>
 *
 * <p>守卫：{@code DomainEndpointsMovedTest} 断言本类不在单体 classpath 上。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionCleanupScheduler {

    private final UserSessionService userSessionService;

    /** 每天 03:17 清理过期/已吊销会话（cron 与拆分前保持一致，避免改变既有节奏） */
    @Scheduled(cron = "0 17 3 * * *")
    public void purgeExpiredSessions() {
        int deleted = userSessionService.purgeExpiredSessions();
        log.info("过期会话清理完成：权威库(flash_sale_auth) 删除 {} 行，副本(旧库) 同步清理", deleted);
    }
}
