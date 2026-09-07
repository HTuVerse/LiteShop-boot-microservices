package org.hongtu.Config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝支付配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    /**
     * 应用ID,您的APPID，收款账号既是您的APPID对应支付宝账号
     */
    private String appId;

    /**
     * 商户私钥，您的PKCS8格式RSA2私钥
     */
    private String privateKey;

    /**
     * 支付宝公钥
     */
    private String publicKey;

    /**
     * 服务器异步通知页面路径
     */
    private String notifyUrl;

    /**
     * 页面跳转同步通知页面路径
     */
    private String returnUrl;

    /**
     * 签名方式
     */
    private String signType = "RSA2";

    /**
     * 字符编码格式
     */
    private String charset = "utf-8";

    /**
     * 支付宝网关
     */
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";

    /**
     * 支付宝网关
     */
    private String sandboxGatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /**
     * 是否使用沙箱环境
     */
    private boolean sandbox = false;
    
    /**
     * 获取支付宝网关地址
     */
    public String getGatewayUrl() {
        return sandbox ? sandboxGatewayUrl : gatewayUrl;
    }
} 