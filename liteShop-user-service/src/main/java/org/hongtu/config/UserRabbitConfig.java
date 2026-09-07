package org.hongtu.config;

import org.hongtu.constant.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户模块RabbitMQ配置类
 */
@Configuration
public class UserRabbitConfig {

    /**
     * 用户交换机
     */
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(RabbitMQConstants.User.EXCHANGE, true, false);
    }

    /**
     * 用户注册队列
     */
    @Bean
    public Queue userRegisterQueue() {
        return QueueBuilder.durable(RabbitMQConstants.User.REGISTER_QUEUE).build();
    }

    /**
     * 绑定用户注册队列到用户交换机
     */
    @Bean
    public Binding bindingUserRegisterQueue(@Qualifier("userRegisterQueue") Queue queue,
                                          @Qualifier("userExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(RabbitMQConstants.User.REGISTER_KEY);
    }
} 