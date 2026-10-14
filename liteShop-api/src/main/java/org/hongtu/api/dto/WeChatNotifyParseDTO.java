package org.hongtu.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 微信支付回调验签解析请求（服务间调用）
 * restaurant-service 收到微信回调后，将原始报文与签名头转发给 pay-service 验签解密
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeChatNotifyParseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 回调地址中的租户，用于选择验签密钥。
     */
    private Long tenantId;

    /**
     * 回调请求体原文
     */
    private String requestBody;

    /**
     * 请求头 Wechatpay-Signature
     */
    private String signature;

    /**
     * 请求头 Wechatpay-Timestamp
     */
    private String timestamp;

    /**
     * 请求头 Wechatpay-Nonce
     */
    private String nonce;

    /**
     * 请求头 Wechatpay-Serial
     */
    private String serial;
}
