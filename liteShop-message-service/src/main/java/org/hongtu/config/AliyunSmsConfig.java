package org.hongtu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云短信服务配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sms")
public class AliyunSmsConfig {

    /**
     * 应用Key
     */
    private String appKey;

    /**
     * 应用Secret
     */
    private String appSecret;

    /**
     * 模板id
     */
    private String templateId;

    /**
     * appcode
     */
    private String appcode;

    /**
     * 请求地址
     */
    private String host;


} 