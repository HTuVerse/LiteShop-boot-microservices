package org.hongtu.config;

import lombok.extern.slf4j.Slf4j;
import org.hongtu.tenant.TenantConstants;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantMqAdvice;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ通用配置类
 */
@Configuration
@EnableRabbit
@Slf4j
public class RabbitMQConfig {

    /**
     * 配置JSON消息转换器
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * 自定义RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 设置消息转换器
        rabbitTemplate.setMessageConverter(messageConverter());
        // 发送消息时自动携带当前租户ID，消费端由 TenantMqAdvice 恢复租户上下文
        rabbitTemplate.setBeforePublishPostProcessors(message -> {
            message.getMessageProperties().setHeader(TenantConstants.MQ_TENANT_HEADER, TenantContext.getTenantId());
            return message;
        });
        // 设置消息确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // 可以记录日志或者进行重试
                System.out.println("消息发送失败：" + cause);
            }
        });
        // 设置消息返回回调
        rabbitTemplate.setReturnsCallback(returned -> {
            System.out.println("消息发送失败：" + returned.getMessage());
        });
        return rabbitTemplate;
    }


    /**
     * 配置RabbitListener容器工厂
     * 关键：为@RabbitListener注解的监听器设置消息转换器
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);

        // 设置消息转换器 - 这是关键！
        factory.setMessageConverter(messageConverter);

        // 消费前从消息头恢复租户上下文，消费后清理（多租户）
        factory.setAdviceChain(new TenantMqAdvice());

        // 设置并发消费者数量（改为1，避免多消费者竞争问题）
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);

        // 设置预取数量（每次只取1条消息）
        factory.setPrefetchCount(1);

        // 设置消息确认模式为手动确认，确保消息处理完才确认
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);

        // 设置默认重新入队为false（消费失败不重新入队，避免死循环）
        factory.setDefaultRequeueRejected(false);

        log.info("========== RabbitListener容器工厂配置完成 ==========");
        log.info("消息转换器: JacksonJsonMessageConverter (Jackson 3)");
        log.info("并发消费者: 1");
        log.info("确认模式: AUTO");
        log.info("================================================");

        return factory;
    }
} 
