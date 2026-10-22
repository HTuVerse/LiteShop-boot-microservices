package org.hongtu.service;

import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.params.AlipayTradeWapPayRequestParams;

import java.util.Map;

/**
 * 按租户支付宝账户执行预下单、查单、关单、退款和回调验签。
 */
public interface AlipayService {
    String createMiniPay(org.hongtu.params.AlipayMiniPayParam param);

    String createWapPay(AlipayTradeWapPayRequestParams request);

    /**
     * 条码支付（被扫）：收银员扫顾客付款码发起扣款。
     * 返回 state：SUCCESS 已支付、WAITING 需轮询查单、FAIL 失败。
     */
    Map<String, Object> createBarCodePay(Long tenantId, String outTradeNo, String subject,
                                         java.math.BigDecimal totalAmountYuan, String authCode);

    /** 撤销交易，用于条码支付轮询超时，返回 retryFlag、action。 */
    Map<String, Object> cancelTrade(Long tenantId, String outTradeNo);

    Map<String, Object> queryTrade(Long tenantId, String outTradeNo);

    /** 关单结果用 outcome 表示 CLOSED、PAID 或 UNKNOWN。 */
    Map<String, Object> closeTrade(Long tenantId, String outTradeNo);

    Map<String, Object> refundTrade(Long tenantId, String outTradeNo, String refundAmount,
                                    String refundReason, String outRequestNo);

    /** 验签并确认通知属于该租户的应用。 */
    boolean verifyNotify(Long tenantId, Map<String, String> params);

    /** 验签后退款通知字段，供订单服务核对。 */
    PaymentRefundNotifyResult parseRefundNotify(Long tenantId, Map<String, String> params);
}
