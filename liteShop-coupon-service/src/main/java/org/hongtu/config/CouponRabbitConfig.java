package org.hongtu.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.constant.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 优惠券模块RabbitMQ配置类
 */
@Slf4j
@Configuration
public class CouponRabbitConfig {

    @Resource
    private AmqpAdmin amqpAdmin;

    @PostConstruct
    public void init() {
        log.info("========== RabbitMQ 优惠券模块配置初始化 ==========");
        log.info("交换机: {}", RabbitMQConstants.Coupon.EXCHANGE);
        log.info("发放队列: {}", RabbitMQConstants.Coupon.ISSUE_QUEUE);
        log.info("路由键: {}", RabbitMQConstants.Coupon.ISSUE_KEY);
        
        // 强制声明交换机、队列和绑定
        try {
            DirectExchange exchange = new DirectExchange(RabbitMQConstants.Coupon.EXCHANGE, true, false);
            Queue issueQueue = QueueBuilder.durable(RabbitMQConstants.Coupon.ISSUE_QUEUE).build();
            Binding issueBinding = BindingBuilder.bind(issueQueue).to(exchange).with(RabbitMQConstants.Coupon.ISSUE_KEY);
            
            amqpAdmin.declareExchange(exchange);
            amqpAdmin.declareQueue(issueQueue);
            amqpAdmin.declareBinding(issueBinding);
            
            log.info("RabbitMQ 队列和绑定声明成功！");
        } catch (Exception e) {
            log.error("RabbitMQ 队列声明失败: {}", e.getMessage(), e);
        }
        log.info("=================================================");
    }

    /**
     * 优惠券交换机
     */
    @Bean
    public DirectExchange couponExchange() {
        return new DirectExchange(RabbitMQConstants.Coupon.EXCHANGE, true, false);
    }

    /**
     * 优惠券发放队列
     */
    @Bean
    public Queue couponIssueQueue() {
        return QueueBuilder.durable(RabbitMQConstants.Coupon.ISSUE_QUEUE).build();
    }

    /**
     * 优惠券过期队列
     */
    @Bean
    public Queue couponExpireQueue() {
        return QueueBuilder.durable(RabbitMQConstants.Coupon.EXPIRE_QUEUE).build();
    }


    /**
     * 优惠券数量查询队列
     */
    @Bean
    public Queue couponCountQueue() {
        return QueueBuilder.durable(RabbitMQConstants.Coupon.COUNT_QUERY_QUEUE).build();
    }


    /**
     * 绑定优惠券发放队列到优惠券交换机
     */
    @Bean
    public Binding bindingCouponIssueQueue(@Qualifier("couponIssueQueue") Queue queue,
                                         @Qualifier("couponExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(RabbitMQConstants.Coupon.ISSUE_KEY);
    }

    /**
     * 绑定优惠券过期队列到优惠券交换机
     */
    @Bean
    public Binding bindingCouponExpireQueue(@Qualifier("couponExpireQueue") Queue queue,
                                          @Qualifier("couponExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(RabbitMQConstants.Coupon.EXPIRE_KEY);
    }

    /**
     * 绑定优惠券数量查询队列到优惠券交换机
     */
    @Bean
    public Binding bindingCouponCountQueue(@Qualifier("couponCountQueue") Queue queue,
                                           @Qualifier("couponExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(RabbitMQConstants.Coupon.COUNT_QUERY_KEY);
    }
} 