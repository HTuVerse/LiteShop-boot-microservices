package org.hongtu.Service;

import org.hongtu.Result.Result;

/**
 * 消息服务接口
 * 处理验证码发送、通知消息等功能
 */
public interface MessageService {

    Result sendEmailVerifyCode(String tenantScope, String email, String prefix, String subject);

    Result sendSmsVerifyCode(String tenantScope, String phone, String prefix);
}
