package org.hongtu.service;

import com.alipay.api.request.AlipayTradePagePayRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.hongtu.params.AlipayTradePagePayRequestParams;
import org.hongtu.params.AlipayTradeWapPayRequestParams;

import java.util.Map;

/**
 * 支付宝支付服务接口
 */
public interface AlipayService {

    /**
     * 创建电脑网站支付
     *
     * @param request 电脑网站支付请求参数
     * @return 支付表单HTML
     */
    String createPagePay(AlipayTradePagePayRequestParams request);

    /**
     * 创建手机网站支付
     *
     * @param request 手机网站支付请求参数
     * @return 支付表单HTML
     */
    String createWapPay(AlipayTradeWapPayRequestParams request);

    /**
     * 交易查询
     *
     * @param outTradeNo 商户订单号
     * @return 查询结果
     */
    Map<String, Object> queryTrade(String outTradeNo);

    /**
     * 交易退款
     *
     * @param outTradeNo   商户订单号
     * @param refundAmount 退款金额
     * @param refundReason 退款原因
     * @return 退款结果
     */
    Map<String, Object> refundTrade(String outTradeNo, String refundAmount, String refundReason);

    /**
     * 交易关闭
     *
     * @param outTradeNo 商户订单号
     * @return 关闭结果
     */
    Map<String, Object> closeTrade(String outTradeNo);

    /**
     * 异步通知处理
     *
     * @param request 请求对象
     * @return 处理结果
     */
    String notifyProcess(HttpServletRequest request);

    /**
     * 同步回调处理
     *
     * @param request 请求对象
     * @return 处理结果
     */
    String returnProcess(HttpServletRequest request);

    /** 验证支付宝异步通知签名及应用ID。 */
    boolean verifyNotify(Map<String, String> params);
}
