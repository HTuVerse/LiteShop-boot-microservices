package org.hongtu.Service.impl;

import cn.hutool.crypto.digest.BCrypt;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.Service.MessageService;
import org.hongtu.utils.CodeGeneratorUtil;
import org.hongtu.utils.EmailUtil;
import org.hongtu.utils.RedisCache;
import org.hongtu.utils.SmsSendUtil;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.Locale;

import static org.hongtu.constant.CommonConst.*;

/**
 * 消息服务实现类
 * 处理验证码发送、通知消息等功能
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private EmailUtil emailUtil;

    @Resource
    private RedisCache redisCache;

    @Resource
    private SmsSendUtil smsSendUtil;

    /**
     * 发送邮箱验证码
     * @param email 邮箱
     * @param prefix 前缀（commonConst）,如登入/注册
     * @param subject 主题
     * @return 发送结果
     */
    @Override
    public Result sendEmailVerifyCode(String tenantScope, String email, String prefix, String subject) {
        if (!emailUtil.isConfigured()) return Result.error(503, "邮件服务未配置，请联系管理员");
        String code = CodeGeneratorUtil.generateCode();
        try {
            emailUtil.sendHtmlMail(email, subject, emailUtil.getHtmlEmailContent(code));
            // 使用 BCrypt 加密存储验证码
            String encryptedCode = BCrypt.hashpw(code);
            redisCache.setCacheObject(cacheKey(prefix, tenantScope, email), encryptedCode,
                    COMMON_EMAIL_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            return Result.success("验证码发送成功");
        } catch (Exception e) {
            return Result.error("验证码发送失败");
        }
    }

    /**
     * 发送短信验证码
     * @param phone 手机号
     * @param prefix 前缀（commonConst）,如登入/注册
     * @return 发送结果
     */
    @Override
    public Result sendSmsVerifyCode(String tenantScope, String phone,String prefix) {
        if (!smsSendUtil.isConfigured()) return Result.error(503, "短信服务未配置，请联系管理员");
        String code = CodeGeneratorUtil.generateCode();
        try {
            boolean success = smsSendUtil.sendVerificationCode(phone, code);
            if (success) {
                // 使用 BCrypt 加密存储验证码
                String encryptedCode = BCrypt.hashpw(code);
                redisCache.setCacheObject(cacheKey(prefix, tenantScope, phone), encryptedCode,
                        COMMON_PHONE_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                return Result.success("手机验证码发送成功");
            } else {
                return Result.error("手机验证码发送失败");
            }
        } catch (Exception e) {
            return Result.error("验证码发送失败");
        }
    }

    private String cacheKey(String prefix, String tenantScope, String account) {
        String scope = tenantScope == null || tenantScope.isBlank()
                ? "PLATFORM" : tenantScope.trim().toUpperCase(Locale.ROOT);
        return prefix + scope + ":" + account;
    }
}
