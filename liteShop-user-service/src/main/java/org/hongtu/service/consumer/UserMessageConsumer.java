package org.hongtu.service.consumer;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.UserMessageDTO;
import org.hongtu.constant.RabbitMQConstants;
import org.hongtu.service.SysUserService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * 用户消息消费者服务
 */
@Slf4j
@Service
public class UserMessageConsumer {

    @Resource
    private SysUserService sysUserService; // 假设已有UserService

    /**
     * 监听用户注册队列
     *
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConstants.User.REGISTER_QUEUE)
    public void handleUserRegister(UserMessageDTO message) {
        log.info("收到用户注册消息: {}", JSON.toJSONString(message));
        try {
            // 处理用户注册后续逻辑，如发送欢迎邮件、赠送新人优惠券等
            log.info("用户注册后续处理成功，用户ID: {}, 用户名: {}", message.getUserId(), message.getUsername());
        } catch (Exception e) {
            log.error("处理用户注册消息异常", e);
        }
    }
} 