package com.ghb.ecommerceflashsalesystem.stream.scheduler;

import com.ghb.ecommerceflashsalesystem.stream.consumer.SeckillOrderConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stream 自动消费调度（第 4 阶段联调/压测所需，默认关闭）
 *
 * 背景：此前消费者只在集成测试里被手动调用（consumePending），生产/演示没有"自动消费"，
 * execute 入队的消息无人建单，订单永远停在 QUEUING、榜单也不会更新。
 *
 * 方案：本调度器周期性拉起消费者。用配置开关 flash.stream.auto-poll 控制：
 *   - 默认不配置 = 关闭（matchIfMissing=false），保证 @SpringBootTest 集成测试仍由测试自行驱动消费，
 *     避免后台 poller 与用例的手动 consume / 失败重试断言产生竞态；
 *   - 浏览器演示 / JMeter 全链路压测等需要"自动异步建单"的场景，启动时置环境变量
 *     FLASH_STREAM_AUTO_POLL=true（或 flash.stream.auto-poll=true）即可开启。
 *
 * 频率考虑：新消息轮询 150ms（削峰吞吐）；PEL 重试轮询 5s（消费失败未 ACK 的消息低频率重投，避免风暴）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flash.stream.auto-poll", havingValue = "true")
public class StreamConsumerScheduler {

    private final SeckillOrderConsumer consumer;

    /** 读 Stream 新消息并建单（高频小批，防止单批阻塞） */
    @Scheduled(fixedDelay = 150)
    public void pollNewMessages() {
        try {
            consumer.consumePending(64);
        } catch (Exception e) {
            log.warn("自动消费新消息异常", e);
        }
    }

    /** 从 PEL 拾起重试消息（低频，失败消息若可恢复则被再次处理） */
    @Scheduled(fixedDelay = 5000)
    public void pollRetryMessages() {
        try {
            consumer.consumerRetry(32);
        } catch (Exception e) {
            log.warn("自动重试消费异常", e);
        }
    }
}
