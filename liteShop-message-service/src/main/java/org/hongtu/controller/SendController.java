package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.Service.MessageService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.utils.DesensitizationUtil;
import org.hongtu.utils.IpUtil;
import org.hongtu.utils.RateLimitUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hongtu.constant.CommonConst.*;

/**
 * 验证码发送控制器
 * 功能:
 * 1. 账号级别限流: 单个账号在时间窗口内最多发送指定次数
 * 2. IP级别限流: 单个IP在时间窗口内最多发送指定次数
 * 3. 发送间隔限制: 同一账号60秒内不能重复发送
 * 4. 日志脱敏: 手机号和邮箱日志输出时自动脱敏
 */
@Slf4j
@RestController
@RequestMapping("/msg")
public class SendController {

    /** 发送限流时间窗口（秒），与 CommonConst.SEND_RESET_TIME 小时一致 */
    private static final int SEND_WINDOW_SEC = SEND_RESET_TIME * 3600;

    /** IP限流时间窗口（秒） */
    private static final int IP_WINDOW_SEC = IP_LIMIT_WINDOW_HOURS * 3600;

    @Resource
    private MessageService messageService;

    @Resource
    private RateLimitUtil rateLimitUtil;

    @Resource
    private IpUtil ipUtil;

    /** 全平台每日发送预算，防止大量账号分摊绕过单账号限额。 */
    @Value("${liteshop.message.global-daily-send-limit:20000}")
    private int globalDailySendLimit;

    /**
     * 发送手机/邮箱验证码（自动识别）
     * 支持场景：登录/注册、重置密码
     *
     * @param account 手机号或邮箱
     * @param type 验证码类型: 1-登录/注册, 2-重置密码（不传默认为1）
     * @param request HTTP请求对象,用于获取IP
     * @return 发送结果
     */
    @PostMapping("/sendVerifyCode")
    @SaIgnore
    public Result sendVerifyCode(@RequestParam("account") String account,
                                 @RequestParam(value = "type", defaultValue = "1") Integer type,
                                 HttpServletRequest request) {
        if (StrUtil.isBlank(account)) {
            return Result.error("账号不能为空");
        }

        boolean isPhone = account.matches(PHONE_REGEX);
        boolean isEmail = account.matches(EMAIL_REGEX);
        if (!isPhone && !isEmail) {
            return Result.error("请输入正确的手机号或邮箱");
        }

        // 根据类型确定验证码前缀和邮件标题
        String codePrefix;
        String emailTitle;
        if (type == 2) {
            // 重置密码场景
            codePrefix = SMS_RESET_CODE_PREFIX;
            emailTitle = "重置密码验证码";
        } else {
            // 默认为登录/注册场景
            codePrefix = SMS_LOGIN_CODE_PREFIX;
            emailTitle = "登录/注册验证码";
        }

        // 复用统一的发送逻辑
        String tenantScope = tenantScope();
        return sendAndRecordLimit(tenantScope, account, request,
                () -> isPhone
                        ? messageService.sendSmsVerifyCode(tenantScope, account, codePrefix)
                        : messageService.sendEmailVerifyCode(tenantScope, account, codePrefix, emailTitle));
    }

    /**
     * 发送手机验证码
     *
     * @param phone 手机号
     * @param request HTTP请求对象,用于获取IP
     * @return 发送结果
     */
    @PostMapping("/sendSmsCode")
    @SaIgnore
    public Result sendSmsCode(@RequestParam("phone") String phone,
                              HttpServletRequest request) {
        if (StrUtil.isBlank(phone)) {
            return Result.error("手机号不能为空");
        }
        if (!phone.matches(PHONE_REGEX)) {
            return Result.error("请输入正确的手机号");
        }

        String tenantScope = tenantScope();
        return sendAndRecordLimit(tenantScope, phone, request,
                () -> messageService.sendSmsVerifyCode(tenantScope, phone, SMS_LOGIN_CODE_PREFIX));
    }

    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱
     * @param request HTTP请求对象,用于获取IP
     * @return 发送结果
     */
    @PostMapping("/sendEmailCode")
    @SaIgnore
    public Result sendEmailCode(@RequestParam("email") String email,
                                HttpServletRequest request) {
        if (StrUtil.isBlank(email)) {
            return Result.error("邮箱不能为空");
        }
        if (!email.matches(EMAIL_REGEX)) {
            return Result.error("请输入正确的邮箱");
        }

        String tenantScope = tenantScope();
        return sendAndRecordLimit(tenantScope, email, request,
                () -> messageService.sendEmailVerifyCode(tenantScope, email, SMS_LOGIN_CODE_PREFIX, "验证码登录"));
    }

    /**
     * 通用的发送 + 限流封装
     * 包含: IP限流、账号限流、发送间隔限制
     */
    private Result sendAndRecordLimit(String tenantScope, String account, HttpServletRequest request,
                                      java.util.function.Supplier<Result> sendAction) {
        // 获取客户端IP
        String clientIp = ipUtil.getClientIp(request);

        // 同一 hash slot 保证 Redis Cluster 可执行组合 Lua；所有配额在实际发送前一次性占用。
        String scope = "{message-send}:";
        String[] keys = {
                scope + "interval:" + tenantScope + ":" + account,
                scope + "ip:" + clientIp,
                scope + "account:" + tenantScope + ":" + account,
                scope + "global"
        };
        int[] limits = {1, IP_MAX_SEND_LIMIT, MAX_SEND_LIMIT, globalDailySendLimit};
        int[] windows = {SEND_INTERVAL_SECONDS, IP_WINDOW_SEC, SEND_WINDOW_SEC, 86400};
        if (!rateLimitUtil.isAllowedAll(keys, limits, windows)) {
            log.warn("验证码发送限额触发, account: {}, IP: {}", DesensitizationUtil.desensitizeAccount(account), clientIp);
            return Result.error("操作过于频繁，请稍后重试");
        }

        try {
            Result result = sendAction.get();
            if (result != null && Result.CODE_SUCCESS.equals(result.getCode())) {
                log.info("验证码发送成功, account: {}, IP: {}", DesensitizationUtil.desensitizeAccount(account), clientIp);
            } else {
                log.warn("验证码发送失败, account: {}", DesensitizationUtil.desensitizeAccount(account));
            }
            return result;
        } catch (Exception e) {
            log.error("发送验证码异常, account: {}", DesensitizationUtil.desensitizeAccount(account), e);
            return Result.error("发送失败，请稍后重试");
        }
    }

    private String tenantScope() {
        Long publicTenantId = TenantContext.getTenantIdOrNull();
        if (publicTenantId != null) {
            return String.valueOf(publicTenantId);
        }
        return "PLATFORM";
    }
}
