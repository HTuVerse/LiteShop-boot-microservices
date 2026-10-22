package org.hongtu.api.dto;

import lombok.Data;

import java.util.Map;

/** 支付服务返回的渠道调起数据。 */
@Data
public class PaymentCreateResult {
    /** 微信 H5 跳转地址，或兼容前端的支付宝表单。 */
    private String payUrl;
    /** 支付宝签名 HTML 表单。 */
    private String payForm;
    /** 扫码支付二维码原始内容。 */
    private String qrCode;
    /** 支付宝小程序交易号。 */
    private String tradeNo;
    /** 微信小程序调起支付参数。 */
    private Map<String, Object> miniPayParams;
}
