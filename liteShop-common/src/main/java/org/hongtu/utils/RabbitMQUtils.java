package org.hongtu.utils;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ工具类，用于发送消息和处理响应
 */
@Slf4j
@Component
public class RabbitMQUtils {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private AmqpAdmin amqpAdmin;

    /**
     * 发送消息到指定交换机和路由键
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息内容
     */
    public void sendMessage(String exchange, String routingKey, Object message) {
        try {
            log.info("发送消息到RabbitMQ: exchange={}, routingKey={}, message={}", exchange, routingKey, message);
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.info("消息发送成功: exchange={}, routingKey={}", exchange, routingKey);
        } catch (Exception e) {
            log.error("消息发送失败，exchange: {}, routingKey: {}, message: {}", exchange, routingKey, message, e);
            throw e;
        }
    }

    /**
     * 发送消息并获取确认结果
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息内容
     * @return 是否发送成功
     */
    public boolean sendMessageWithConfirm(String exchange, String routingKey, Object message) {
        try {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

            // 发送消息
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);

            // 获取确认结果
            correlationData.getFuture().whenComplete((confirm, ex) -> {
                if (ex != null) {
                    log.error("消息发送异常", ex);
                    resultFuture.completeExceptionally(ex);
                } else if (confirm != null) {
                    if (confirm.ack()) {
                        resultFuture.complete(true);
                    } else {
                        log.warn("消息发送失败，exchange: {}, routingKey: {}, cause: {}",
                                exchange, routingKey, confirm.reason());
                        resultFuture.complete(false);
                    }
                }
            });
            // 等待确认结果，最多等待5秒
            boolean confirmed = resultFuture.get(5, TimeUnit.SECONDS);
            if (confirmed && correlationData.getReturned() != null) {
                log.error("消息无法路由，exchange={}, routingKey={}, replyText={}",
                        exchange, routingKey, correlationData.getReturned().getReplyText());
                return false;
            }
            return confirmed;
        } catch (Exception e) {
            log.error("消息发送或确认过程中发生异常", e);
            return false;
        }
    }

    /**
     * 发送消息到指定交换机和路由键，并设置延迟时间
     * 使用 x-delay 消息头实现延迟队列（需要 RabbitMQ 延迟插件 rabbitmq_delayed_message_exchange）
     *
     * @param exchange   交换机（需要是 x-delayed-message 类型）
     * @param routingKey 路由键
     * @param message    消息内容
     * @param delayTime  延迟时间（毫秒）
     */
    public void sendDelayMessage(String exchange, String routingKey, Object message, long delayTime) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
                // 使用 x-delay 消息头设置延迟时间（替代已弃用的 setDelay 方法）
                msg.getMessageProperties().setHeader("x-delay", delayTime);
                return msg;
            });
            log.info("延迟消息发送成功: exchange={}, routingKey={}, delayTime={}ms", exchange, routingKey, delayTime);
        } catch (Exception e) {
            log.error("延迟消息发送失败，exchange: {}, routingKey: {}, delayTime: {}",
                    exchange, routingKey, delayTime, e);
            throw e;
        }
    }

    /**
     * 批量发送消息
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param messages   消息列表
     */
    public void sendBatchMessages(String exchange, String routingKey, List<?> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            for (Object message : messages) {
                rabbitTemplate.convertAndSend(exchange, routingKey, message);
            }
            log.info("批量发送消息成功，共发送{}条消息", messages.size());
        } catch (Exception e) {
            log.error("批量发送消息失败，exchange: {}, routingKey: {}, messageCount: {}",
                    exchange, routingKey, messages.size(), e);
            throw e;
        }
    }

    /**
     * 发送请求并等待响应（同步方法）
     *
     * @param exchange    请求交换机
     * @param routingKey  请求路由键
     * @param request     请求内容
     * @param timeout     超时时间（毫秒）
     * @return            响应内容，如果超时或异常则返回null
     */
    public Object sendAndReceive(String exchange, String routingKey, Object request, long timeout) {
        try {
            // 创建一个唯一的回调队列名
            String callbackQueue = "callback." + UUID.randomUUID().toString();
            // 创建临时回调队列（自动删除、排他性）
            Queue queue = new Queue(callbackQueue, false, true, true);
            amqpAdmin.declareQueue(queue);

            // 如果请求是Map类型，添加回调队列信息
            if (request instanceof Map) {
                ((Map<String, Object>) request).put("callbackQueue", callbackQueue);
            }

            // 发送请求
            log.info("发送同步请求: exchange={}, routingKey={}", exchange, routingKey);
            rabbitTemplate.convertAndSend(exchange, routingKey, request);

            // 等待响应
            Object response = rabbitTemplate.receiveAndConvert(callbackQueue, timeout);

            // 删除临时队列
            amqpAdmin.deleteQueue(callbackQueue);

            return response;
        } catch (Exception e) {
            log.error("发送请求或接收响应时发生异常", e);
            return null;
        }
    }

    /**
     * 发送请求并异步获取响应
     *
     * @param exchange    请求交换机
     * @param routingKey  请求路由键
     * @param request     请求内容
     * @param timeout     超时时间（毫秒）
     * @return            包含响应的CompletableFuture
     */
    public CompletableFuture<Object> sendAndReceiveAsync(String exchange, String routingKey, Object request, long timeout) {
        CompletableFuture<Object> future = new CompletableFuture<>();

        try {
            // 创建一个唯一的回调队列名
            String callbackQueue = "async.callback." + UUID.randomUUID().toString();
            // 创建临时回调队列（自动删除、排他性）
            Queue queue = new Queue(callbackQueue, false, true, true);
            amqpAdmin.declareQueue(queue);

            // 如果请求是Map类型，添加回调队列信息
            if (request instanceof Map) {
                ((Map<String, Object>) request).put("callbackQueue", callbackQueue);
            }

            // 发送请求
            log.info("发送异步请求: exchange={}, routingKey={}", exchange, routingKey);
            rabbitTemplate.convertAndSend(exchange, routingKey, request);

            // 异步等待响应
            CompletableFuture.runAsync(() -> {
                try {
                    Object response = rabbitTemplate.receiveAndConvert(callbackQueue, timeout);
                    if (response != null) {
                        future.complete(response);
                    } else {
                        future.completeExceptionally(new TimeoutException("接收响应超时"));
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                } finally {
                    // 删除临时队列
                    amqpAdmin.deleteQueue(callbackQueue);
                }
            });

            return future;
        } catch (Exception e) {
            log.error("发送异步请求时发生异常", e);
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * 发送带有自定义属性的消息
     *
     * @param exchange    交换机
     * @param routingKey  路由键
     * @param message     消息内容
     * @param properties  自定义属性
     */
    public void sendMessageWithProperties(String exchange, String routingKey, Object message, Map<String, Object> properties) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
                MessageProperties messageProperties = msg.getMessageProperties();
                if (properties != null && !properties.isEmpty()) {
                    properties.forEach(messageProperties::setHeader);
                }
                return msg;
            });
        } catch (Exception e) {
            log.error("发送带属性消息失败", e);
            throw e;
        }
    }
} 
