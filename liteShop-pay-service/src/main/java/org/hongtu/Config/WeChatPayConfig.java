package org.hongtu.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付配置类
 * 
 * @author Ht
 * @since 2025-10-31
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.pay")
public class WeChatPayConfig {

    /**
     * 小程序APPID
     */
    private String appId;

    /**
     * 商户号
     */
    private String mchId;

    /**
     * 商户API证书序列号
     */
    private String mchSerialNo;

    /**
     * 商户私钥路径（apiclient_key.pem）
     */
    private String privateKeyPath;

    /**
     * APIv3密钥（用于解密回调通知）
     */
    private String apiV3Key;

    /**
     * 支付回调通知地址
     */
    private String notifyUrl;

    /**
     * 证书路径（apiclient_cert.pem）
     */
    private String certPath;

    /**
     * 证书密钥路径（apiclient_cert.p12）
     */
    private String certP12Path;

    /**
     * 是否使用沙箱环境
     */
    private boolean sandbox = false;

    /**
     * 微信支付API域名
     */
    private String apiUrl = "https://api.mch.weixin.qq.com";

    /**
     * 沙箱API域名
     */
    private String sandboxApiUrl = "https://api.mch.weixin.qq.com/sandboxnew";

    /**
     * 获取API地址
     */
    public String getApiUrl() {
        return sandbox ? sandboxApiUrl : apiUrl;
    }
}

