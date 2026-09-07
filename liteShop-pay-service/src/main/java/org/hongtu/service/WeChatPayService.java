package org.hongtu.service;

import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.params.WeChatPayTradeParam;

import java.util.Map;

/**
 * 微信支付服务接口
 * 
 * @author Ht
 * @since 2025-10-31
 */
public interface WeChatPayService {

    /**
     * 创建小程序支付（JSAPI）
     *
     * @param param 支付参数
     * @return 支付参数（返回给小程序用于调起支付）
     */
    Map<String, Object> createJsapiPay(WeChatPayTradeParam param);

    /**
     * 创建Native支付（扫码支付）
     *
     * @param param 支付参数
     * @return 二维码链接
     */
    String createNativePay(WeChatPayTradeParam param);

    /**
     * 创建H5支付
     *
     * @param param 支付参数
     * @return 支付跳转链接
     */
    String createH5Pay(WeChatPayTradeParam param);

    /**
     * 交易查询
     *
     * @param outTradeNo 商户订单号
     * @return 查询结果
     */
    Map<String, Object> queryTrade(String outTradeNo);

    /**
     * 关闭订单
     *
     * @param outTradeNo 商户订单号
     * @return 关闭结果
     */
    Map<String, Object> closeTrade(String outTradeNo);

    /**
     * 申请退款
     *
     * @param outTradeNo   商户订单号
     * @param outRefundNo  商户退款单号
     * @param refundAmount 退款金额
     * @param totalAmount  订单总金额
     * @param reason       退款原因
     * @return 退款结果
     */
    Map<String, Object> refund(String outTradeNo, String outRefundNo, 
                               String refundAmount, String totalAmount, String reason);

    /**
     * 查询退款
     *
     * @param outRefundNo 商户退款单号
     * @return 退款查询结果
     */
    Map<String, Object> queryRefund(String outRefundNo);

    /**
     * 验证并解析微信支付回调通知（纯粹的验签和解密，不处理业务）
     * 【推荐】各业务模块应该调用此方法获取解析后的数据，然后自己处理业务逻辑
     *
     * @param requestBody 回调请求体（加密数据）
     * @param signature   签名
     * @param timestamp   时间戳
     * @param nonce       随机串
     * @param serial      证书序列号
     * @return 解析后的支付通知数据
     * @throws RuntimeException 验签失败或解析失败时抛出异常
     */
    PaymentNotifyResult parseAndVerifyNotify(
            String requestBody, String signature, String timestamp, String nonce, String serial);

    /**
     * 处理退款回调通知
     *
     * @param requestBody 回调请求体
     * @param signature   签名
     * @param timestamp   时间戳
     * @param nonce       随机串
     * @param serial      证书序列号
     * @return 处理结果
     */
    String handleRefundNotify(String requestBody, String signature, String timestamp, 
                             String nonce, String serial);
}

