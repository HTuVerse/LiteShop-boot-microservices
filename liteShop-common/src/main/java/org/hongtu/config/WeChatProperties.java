package org.hongtu.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
 
@Component
@ConfigurationProperties(prefix = "wx")
@Data
public class WeChatProperties {
 
    private String appid; //小程序的appid
    private String secret; //小程序的秘钥
}