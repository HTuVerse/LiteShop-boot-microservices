package org.hongtu.api.client;

import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.api.dto.PaymentCreateCommand;
import org.hongtu.api.dto.PaymentCreateResult;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.params.AlipayBarCodePayParam;
import org.hongtu.params.AlipayPayTradeParam;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.params.WeChatMicroPayParam;
import org.hongtu.params.WeChatPayTradeParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 支付服务内部命令。调用方必须传入订单所属租户，金额和退款单号由订单服务生成。
 */
@FeignClient(name = "liteShop-pay-service", contextId = "payFeignClient")
public interface PayFeignClient {

    /** 按租户账户和渠道场景创建支付交易。 */
    @PostMapping("/inner/pay/create")
    Result<PaymentCreateResult> createPayment(@RequestBody PaymentCreateCommand command);

    @PostMapping("/inner/pay/wechat/jsapi")
    Result<Map<String, Object>> createWeChatJsapiPay(@RequestBody WeChatPayTradeParam param);

    @PostMapping("/inner/pay/wechat/h5")
    Result<String> createWeChatH5Pay(@RequestBody WeChatPayTradeParam param);

    /** 微信 Native 扫码预下单。 */
    @PostMapping("/inner/pay/wechat/native")
    Result<String> createWeChatNativePay(@RequestBody WeChatPayTradeParam param);

    @PostMapping("/inner/pay/alipay/mini")
    Result<String> createAlipayMiniPay(@RequestBody org.hongtu.params.AlipayMiniPayParam param);

    /** 支付宝网页 JSAPI 创建交易，返回支付宝交易号。 */
    @PostMapping("/inner/pay/alipay/jsapi")
    Result<String> createAlipayJsapiPay(@RequestBody org.hongtu.params.AlipayMiniPayParam param);

    /** 微信付款码（被扫）支付。 */
    @PostMapping("/inner/pay/wechat/micropay")
    Result<Map<String, Object>> createWeChatMicroPay(@RequestBody WeChatMicroPayParam param);

    /** 查询微信付款码订单支付结果。 */
    @GetMapping("/inner/pay/wechat/micropay/query")
    Result<Map<String, Object>> queryWeChatMicroPay(@RequestParam("tenantId") Long tenantId,
                                                    @RequestParam("outTradeNo") String outTradeNo);

    /** 撤销微信付款码订单。 */
    @PostMapping("/inner/pay/wechat/micropay/reverse")
    Result<Map<String, Object>> reverseWeChatMicroPay(@RequestParam("tenantId") Long tenantId,
                                                      @RequestParam("outTradeNo") String outTradeNo);

    @GetMapping("/inner/pay/wechat/query")
    Result<Map<String, Object>> queryWeChatTrade(@RequestParam("tenantId") Long tenantId,
                                                 @RequestParam("outTradeNo") String outTradeNo);

    @PostMapping("/inner/pay/wechat/close")
    Result<Map<String, Object>> closeWeChatTrade(@RequestParam("tenantId") Long tenantId,
                                                 @RequestParam("outTradeNo") String outTradeNo);

    /** 发起微信退款，金额单位均为分。 */
    @PostMapping("/inner/pay/wechat/refund")
    Result<Map<String, Object>> refundWeChatTrade(@RequestParam("tenantId") Long tenantId,
                                                   @RequestParam("outTradeNo") String outTradeNo,
                                                   @RequestParam("outRefundNo") String outRefundNo,
                                                   @RequestParam("refundAmount") String refundAmount,
                                                   @RequestParam("totalAmount") String totalAmount,
                                                   @RequestParam(value = "reason", required = false) String reason,
                                                   @RequestParam("notifyUrl") String notifyUrl);

    /** 餐厅退款的渠道通知地址由支付服务从本租户账户生成。 */
    @PostMapping("/inner/pay/wechat/refund/restaurant")
    Result<Map<String, Object>> refundRestaurantWeChatTrade(@RequestParam("tenantId") Long tenantId,
                                                              @RequestParam("outTradeNo") String outTradeNo,
                                                              @RequestParam("outRefundNo") String outRefundNo,
                                                              @RequestParam("refundAmount") String refundAmount,
                                                              @RequestParam("totalAmount") String totalAmount,
                                                              @RequestParam(value = "reason", required = false) String reason);

    @GetMapping("/inner/pay/wechat/refund/query")
    Result<Map<String, Object>> queryWeChatRefund(@RequestParam("tenantId") Long tenantId,
                                                  @RequestParam("outRefundNo") String outRefundNo);

    @PostMapping("/inner/pay/wechat/parse-notify")
    Result<PaymentNotifyResult> parseWeChatNotify(@RequestBody WeChatNotifyParseDTO dto);

    @PostMapping("/inner/pay/wechat/parse-refund-notify")
    Result<PaymentRefundNotifyResult> parseWeChatRefundNotify(@RequestBody WeChatNotifyParseDTO dto);

    @PostMapping("/inner/pay/alipay/wap")
    Result<String> createAlipayWapPay(@RequestBody AlipayTradeWapPayRequestParams param);

    /** 支付宝电脑网站跳转表单。 */
    @PostMapping("/inner/pay/alipay/page")
    Result<String> createAlipayPagePay(@RequestBody AlipayPayTradeParam param);

    /** 支付宝当面付预创建二维码。 */
    @PostMapping("/inner/pay/alipay/precreate")
    Result<String> createAlipayPrecreatePay(@RequestBody AlipayPayTradeParam param);

    /** 支付宝条码（被扫）支付。 */
    @PostMapping("/inner/pay/alipay/barcode")
    Result<Map<String, Object>> createAlipayBarCodePay(@RequestBody AlipayBarCodePayParam param);

    /** 撤销支付宝交易（条码支付轮询超时使用）。 */
    @PostMapping("/inner/pay/alipay/cancel")
    Result<Map<String, Object>> cancelAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                  @RequestParam("outTradeNo") String outTradeNo);

    @GetMapping("/inner/pay/alipay/query")
    Result<Map<String, Object>> queryAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                 @RequestParam("outTradeNo") String outTradeNo);

    @PostMapping("/inner/pay/alipay/close")
    Result<Map<String, Object>> closeAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                 @RequestParam("outTradeNo") String outTradeNo);

    @PostMapping("/inner/pay/alipay/refund")
    Result<Map<String, Object>> refundAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                   @RequestParam("outTradeNo") String outTradeNo,
                                                   @RequestParam("outRequestNo") String outRequestNo,
                                                   @RequestParam("refundAmount") String refundAmount,
                                                   @RequestParam(value = "refundReason", required = false) String refundReason);

    @PostMapping("/inner/pay/alipay/verify-notify")
    Result<Boolean> verifyAlipayNotify(@RequestParam("tenantId") Long tenantId,
                                       @RequestBody Map<String, String> params);

    @PostMapping("/inner/pay/alipay/parse-refund-notify")
    Result<PaymentRefundNotifyResult> parseAlipayRefundNotify(@RequestParam("tenantId") Long tenantId,
                                                              @RequestBody Map<String, String> params);
}
