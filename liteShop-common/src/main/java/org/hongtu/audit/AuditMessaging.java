package org.hongtu.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 审计队列和采集切面配置。 */
@Configuration
@EnableAspectJAutoProxy
@EnableScheduling
public class AuditMessaging {
    public static final String QUEUE = "liteshop.system-audit.v1";

    @Bean
    public Queue systemAuditQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public ObjectMapper auditObjectMapper() {
        return new ObjectMapper();
    }
}
