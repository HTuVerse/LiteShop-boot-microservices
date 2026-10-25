package org.hongtu.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 审计队列和采集切面配置。 */
@Configuration
@EnableAspectJAutoProxy
@EnableScheduling
public class AuditMessaging {
    public static final String LEGACY_QUEUE = "liteshop.system-audit.v1";
    public static final String QUEUE = "liteshop.system-audit.v2";
    public static final String DEAD_LETTER_QUEUE = "liteshop.system-audit.dead.v2";

    @Bean
    public Queue systemAuditQueue() {
        // 旧队列不能原地增加死信参数，新投递统一进入带死信路由的 v2 队列。
        return QueueBuilder.durable(QUEUE).deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue legacySystemAuditQueue() {
        return new Queue(LEGACY_QUEUE, true);
    }

    @Bean
    public Queue systemAuditDeadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public ObjectMapper auditObjectMapper() {
        return new ObjectMapper();
    }
}
