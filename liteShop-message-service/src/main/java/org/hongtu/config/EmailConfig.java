package org.hongtu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 功能：
 * 作者：
 * 时间：2025/7/11 上午2:36
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mail")
public class EmailConfig {


    private String mail;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String from;
}
