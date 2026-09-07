package org.hongtu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 七牛云OSS配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qiniu")
public class QiniuConfig {
    /**
     * 七牛云AccessKey
     */
    private String accessKey;
    
    /**
     * 七牛云SecretKey
     */
    private String secretKey;
    
    /**
     * 存储空间名称
     */
    private String bucket;
    
    /**
     * 访问域名
     */
    private String domain;

} 