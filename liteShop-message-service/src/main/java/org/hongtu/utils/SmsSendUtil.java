package org.hongtu.utils;

import com.alibaba.cloudapi.sdk.model.ApiCallback;
import com.alibaba.cloudapi.sdk.model.ApiRequest;
import com.alibaba.cloudapi.sdk.model.ApiResponse;
import com.alibaba.cloudapi.sdk.model.HttpClientBuilderParams;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.utils.smsAliSdk.httpsSmsApiSdk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 短信发送工具类
 * 使用阿里云短信服务发送验证码
 */
@Slf4j
@Component
public class SmsSendUtil {

    @Resource
    RedisCache redisCache;

    @Value("${sms.appKey:}")
    private String appKey;

    @Value("${sms.appSecret:}")
    private String appSecret;

    @Value("${sms.templateId:}")
    private String templateId;

    @Value("${sms.appcode:}")
    private String appcode;

    @Value("${sms.host:}")
    private String host;
    /**
     * 初始化短信客户端
     */
    @PostConstruct
    public void init() {
        if (!isConfigured()) {
            log.warn("短信通道未配置，验证码发送暂不可用");
            return;
        }
        HttpClientBuilderParams httpsParam = new HttpClientBuilderParams();
        httpsParam.setAppKey(appKey);
        httpsParam.setAppSecret(appSecret);
        // 设置连接超时时间
        httpsParam.setReadTimeout(10000L);
        httpsParam.setConnectionTimeout(5000L);
        // 初始化HTTPS客户端
        httpsSmsApiSdk.getInstance().init(httpsParam);
        log.info("短信服务初始化完成");
    }

    /**
     * 发送验证码短信（使用默认模板）
     *
     * @param phoneNumber 手机号码
     * @param code 验证码
     * @return 是否发送成功
     */
    public boolean isConfigured() {
        return appKey != null && !appKey.isBlank() && appSecret != null && !appSecret.isBlank()
                && templateId != null && !templateId.isBlank();
    }

    public boolean sendVerificationCode(String phoneNumber, String code) {
        return sendVerificationCode(phoneNumber, templateId, code);
    }

    /**
     * 发送验证码短信（指定模板）
     *
     * @param phoneNumber 手机号码
     * @param templateId 模板ID
     * @param code 验证码
     * @return 是否发送成功
     */
    public boolean sendVerificationCode(String phoneNumber, String templateId, String code) {
        if (!isConfigured()) return false;
        String content = "code:" + code;
        try {
            ApiResponse response = httpsSmsApiSdk.getInstance().sendSmsSyncMode(content, templateId, phoneNumber);
            if (response != null && response.getCode() == 200) {
                String responseBody = new String(response.getBody(), StandardCharsets.UTF_8);
                log.info("短信发送成功, 手机号: {}, 响应: {}", DesensitizationUtil.desensitizePhone(phoneNumber), responseBody);
                return true;
            } else {
                log.error("短信发送失败, 手机号: {}, 错误码: {}", DesensitizationUtil.desensitizePhone(phoneNumber),
                        response != null ? response.getCode() : "未知");
                return false;
            }
        } catch (Exception e) {
            log.error("短信发送异常, 手机号: {}", DesensitizationUtil.desensitizePhone(phoneNumber), e);
            return false;
        }
    }

    /**
     * 异步发送验证码短信（使用默认模板）
     *
     * @param phoneNumber 手机号码
     * @param code 验证码
     * @param callback 回调函数
     */
    public void sendVerificationCodeAsync(String phoneNumber, String code, SmsCallback callback) {
        sendVerificationCodeAsync(phoneNumber, templateId, code, callback);
    }

    /**
     * 异步发送验证码短信（指定模板）
     *
     * @param phoneNumber 手机号码
     * @param templateId 模板ID
     * @param code 验证码
     * @param callback 回调函数
     */
    public void sendVerificationCodeAsync(String phoneNumber, String templateId, String code, SmsCallback callback) {
        if (!isConfigured()) {
            callback.onComplete(false, "短信服务未配置");
            return;
        }
        String content = "code:" + code;
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};
        final String[] responseMsg = {null};

        httpsSmsApiSdk.getInstance().sendSms(content, templateId, phoneNumber, new ApiCallback() {
            @Override
            public void onFailure(ApiRequest request, Exception e) {
                log.error("短信发送失败, 手机号: {}", DesensitizationUtil.desensitizePhone(phoneNumber), e);
                responseMsg[0] = e.getMessage();
                latch.countDown();
            }

            @Override
            public void onResponse(ApiRequest request, ApiResponse response) {
                if (response != null && response.getCode() == 200) {
                    String responseBody = new String(response.getBody(), StandardCharsets.UTF_8);
                    log.info("短信发送成功, 手机号: {}, 响应: {}", DesensitizationUtil.desensitizePhone(phoneNumber), responseBody);
                    success[0] = true;
                    responseMsg[0] = responseBody;
                } else {
                    log.error("短信发送失败, 手机号: {}, 错误码: {}", DesensitizationUtil.desensitizePhone(phoneNumber),
                            response != null ? response.getCode() : "未知");
                    responseMsg[0] = "错误码: " + (response != null ? response.getCode() : "未知");
                }
                latch.countDown();
            }
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
            if (callback != null) {
                callback.onComplete(success[0], responseMsg[0]);
            }
        } catch (InterruptedException e) {
            log.error("等待短信发送响应被中断", e);
            if (callback != null) {
                callback.onComplete(false, "发送超时");
            }
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 发送短信(不加签名,使用默认模板)
     *
     * @param phoneNumber 手机号码
     * @param code 验证码
     * @return 发送结果
     */
    public boolean sendSmsWithNoSignature(String phoneNumber, String code) {
        return sendSmsWithNoSignature(phoneNumber, templateId, code);
    }

    /**
     * 发送短信(不加签名,指定模板)
     *
     * @param phoneNumber 手机号码
     * @param templateId 模板ID
     * @param code 验证码
     * @return 发送结果
     */
    public boolean sendSmsWithNoSignature(String phoneNumber, String templateId, String code) {
        try {
            String content = "code:" + code;
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "APPCODE " + appcode);
            headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("phone_number", phoneNumber);
            queryParams.put("template_id", templateId);
            queryParams.put("content", content);

            try {
                String json = HttpClientUtil.doPost(host, headers, queryParams);
                String status = JSON.parseObject(json).getString("status");
                boolean success = "OK".equals(status);

                if (success) {
                    log.info("短信发送成功, 手机号: {}", DesensitizationUtil.desensitizePhone(phoneNumber));
                } else {
                    log.error("短信发送失败, 手机号: {}, 响应: {}", DesensitizationUtil.desensitizePhone(phoneNumber), status);
                }
                return success;
            } catch (Exception e) {
                log.error("短信发送异常, 手机号: {}", DesensitizationUtil.desensitizePhone(phoneNumber), e);
                return false;
            }
        } catch (Exception e) {
            log.error("短信发送失败, 手机号: {}", DesensitizationUtil.desensitizePhone(phoneNumber), e);
            throw new RuntimeException("短信发送失败", e);
        }
    }

    /**
     * 短信回调接口
     */
    public interface SmsCallback {
        /**
         * 完成回调
         * @param success 是否成功
         * @param message 响应消息
         */
        void onComplete(boolean success, String message);
    }
} 