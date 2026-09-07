package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.Service.MessageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息服务内部接口（仅供其他微服务通过 Feign 调用）
 * 外部经网关访问 /inner/** 会被全局过滤器拦截
 */
@Slf4j
@RestController
@RequestMapping("/inner/msg")
public class InnerMessageController {

    @Resource
    private MessageService messageService;

    /**
     * 发送短信验证码（内部调用，限流由业务调用方或对外接口负责）
     *
     * @param phone  手机号
     * @param prefix 验证码 Redis Key 前缀（区分登录/重置密码等场景）
     */
    @PostMapping("/sms")
    @SaIgnore
    public Result sendSmsVerifyCode(@RequestParam("phone") String phone,
                                    @RequestParam("prefix") String prefix,
                                    @RequestParam(value = "tenantScope", required = false) String tenantScope) {
        return messageService.sendSmsVerifyCode(tenantScope, phone, prefix);
    }

    /**
     * 发送邮箱验证码（内部调用）
     *
     * @param email   邮箱
     * @param prefix  验证码 Redis Key 前缀
     * @param subject 邮件标题
     */
    @PostMapping("/email")
    @SaIgnore
    public Result sendEmailVerifyCode(@RequestParam("email") String email,
                                      @RequestParam("prefix") String prefix,
                                      @RequestParam("subject") String subject,
                                      @RequestParam(value = "tenantScope", required = false) String tenantScope) {
        return messageService.sendEmailVerifyCode(tenantScope, email, prefix, subject);
    }
}
