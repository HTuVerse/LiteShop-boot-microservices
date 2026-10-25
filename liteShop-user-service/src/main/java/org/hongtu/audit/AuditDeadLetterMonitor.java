package org.hongtu.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 监测待人工重放的审计死信，避免消费失败后长期无人发现。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditDeadLetterMonitor {
    private final RabbitTemplate rabbit;

    @Scheduled(fixedDelayString = "${liteshop.audit.dead-letter-check-interval-ms:60000}")
    public void check() {
        try {
            int count = rabbit.execute(channel -> channel.queueDeclarePassive(
                    AuditMessaging.DEAD_LETTER_QUEUE).getMessageCount());
            if (count > 0) {
                log.error("审计死信队列存在待处理事件，queue={}, count={}",
                        AuditMessaging.DEAD_LETTER_QUEUE, count);
            }
        } catch (RuntimeException error) {
            log.error("审计死信队列状态检查失败", error);
        }
    }
}
