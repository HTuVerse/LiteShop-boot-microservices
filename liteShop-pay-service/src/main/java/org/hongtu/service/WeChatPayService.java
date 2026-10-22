package org.hongtu.service;

import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.params.WeChatPayTradeParam;

import java.util.Map;

/**
 * 按租户微信账户执行预下单、查单、关单、退款和回调验签。
 */
public interface WeChatPayService {

    /** 使用服务端金额和订单号创建 JSAPI 预下单。 */
    Map<String, Object> createJsapiPay(WeChatPayTradeParam param);

    /** 微信浏览器外的 H5 支付链接。 */
    String createH5Pay(WeChatPayTradeParam param);

    /** 微信 Native 扫码预下单，返回二维码原始内容。 */
    String createNativePay(WeChatPayTradeParam param);

    /**
     * 付款码（被扫）支付：收银员扫顾客付款码发起扣款。
     * 返回 state：SUCCESS 已支付、USERPAYING 需轮询查单、FAIL 失败。
     */
    Map<String, Object> createMicroPay(Long tenantId, String outTradeNo, String description,
                                       java.math.BigDecimal totalAmountYuan, String authCode);

    /** 查询付款码订单支付结果，返回 state：SUCCESS/USERPAYING/FAIL。 */
    Map<String, Object> queryMicroPay(Long tenantId, String outTradeNo);

    /** 撤销付款码订单，返回 recall 表示是否需要继续轮询撤销。 */
    Map<String, Object> reverseMicroPay(Long tenantId, String outTradeNo);

    Map<String, Object> queryTrade(Long tenantId, String outTradeNo);

    /** 关单结果用 outcome 表示 CLOSED、PAID 或 UNKNOWN。 */
    Map<String, Object> closeTrade(Long tenantId, String outTradeNo);

    Map<String, Object> refund(Long tenantId, String outTradeNo, String outRefundNo,
                               String refundAmount, String totalAmount, String reason, String notifyUrl);

    Map<String, Object> queryRefund(Long tenantId, String outRefundNo);

    PaymentNotifyResult parseAndVerifyNotify(Long tenantId, String requestBody, String signature,
                                             String timestamp, String nonce, String serial);

    /** 只验签解密退款通知，不修改订单。 */
    PaymentRefundNotifyResult parseRefundNotify(Long tenantId, String requestBody, String signature,
                                                String timestamp, String nonce, String serial);
}
