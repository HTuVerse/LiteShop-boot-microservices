package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.params.AlipayBarCodePayParam;
import org.hongtu.params.AlipayPayTradeParam;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.params.WeChatMicroPayParam;
import org.hongtu.params.WeChatPayTradeParam;
import org.hongtu.service.AlipayService;
import org.hongtu.service.WeChatPayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付内部命令。只接受其他服务携带内部凭据的调用，退款单号和金额由订单服务生成。
 */
@RestController
@RequestMapping("/inner/pay")
@SaIgnore
@RequiredArgsConstructor
public class InnerPayController {
    private final WeChatPayService weChatPayService;
    private final AlipayService alipayService;

    /** 微信 JSAPI 预下单。 */
    @PostMapping("/wechat/jsapi")
    public Result<Map<String, Object>> createWeChatJsapiPay(@RequestBody WeChatPayTradeParam param) {
        return Result.success(weChatPayService.createJsapiPay(param));
    }

    @PostMapping("/wechat/h5")
    public Result<String> createWeChatH5Pay(@RequestBody WeChatPayTradeParam param) {
        return Result.success(weChatPayService.createH5Pay(param));
    }

    /** 微信 Native 扫码预下单。 */
    @PostMapping("/wechat/native")
    public Result<String> createWeChatNativePay(@RequestBody WeChatPayTradeParam param) {
        return Result.success(weChatPayService.createNativePay(param));
    }

    @PostMapping("/alipay/mini")
    public Result<String> createAlipayMiniPay(@RequestBody org.hongtu.params.AlipayMiniPayParam param) {
        return Result.success(alipayService.createMiniPay(param));
    }

    /** 支付宝 JSAPI 支付复用授权码换取买家 ID 的交易创建流程。 */
    @PostMapping("/alipay/jsapi")
    public Result<String> createAlipayJsapiPay(@RequestBody org.hongtu.params.AlipayMiniPayParam param) {
        return Result.success(alipayService.createMiniPay(param));
    }

    /** 微信付款码（被扫）支付。 */
    @PostMapping("/wechat/micropay")
    public Result<Map<String, Object>> createWeChatMicroPay(@RequestBody WeChatMicroPayParam param) {
        return Result.success(weChatPayService.createMicroPay(param.getTenantId(), param.getOutTradeNo(),
                param.getDescription(), param.getTotalAmount(), param.getAuthCode()));
    }

    /** 查询微信付款码订单支付结果。 */
    @GetMapping("/wechat/micropay/query")
    public Result<Map<String, Object>> queryWeChatMicroPay(@RequestParam("tenantId") Long tenantId,
                                                           @RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(weChatPayService.queryMicroPay(tenantId, outTradeNo));
    }

    /** 撤销微信付款码订单。 */
    @PostMapping("/wechat/micropay/reverse")
    public Result<Map<String, Object>> reverseWeChatMicroPay(@RequestParam("tenantId") Long tenantId,
                                                             @RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(weChatPayService.reverseMicroPay(tenantId, outTradeNo));
    }

    /** 按租户商户查询微信订单。 */
    @GetMapping("/wechat/query")
    public Result<Map<String, Object>> queryWeChatTrade(@RequestParam("tenantId") Long tenantId,
                                                        @RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(weChatPayService.queryTrade(tenantId, outTradeNo));
    }

    /** 按租户商户关闭微信订单。 */
    @PostMapping("/wechat/close")
    public Result<Map<String, Object>> closeWeChatTrade(@RequestParam("tenantId") Long tenantId,
                                                        @RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(weChatPayService.closeTrade(tenantId, outTradeNo));
    }

    /** 发起微信退款。outRefundNo 必须由订单服务生成。 */
    @PostMapping("/wechat/refund")
    public Result<Map<String, Object>> refundWeChatTrade(@RequestParam("tenantId") Long tenantId,
                                                          @RequestParam("outTradeNo") String outTradeNo,
                                                          @RequestParam("outRefundNo") String outRefundNo,
                                                          @RequestParam("refundAmount") String refundAmount,
                                                          @RequestParam("totalAmount") String totalAmount,
                                                          @RequestParam(value = "reason", required = false) String reason,
                                                          @RequestParam("notifyUrl") String notifyUrl) {
        return Result.success(weChatPayService.refund(
                tenantId, outTradeNo, outRefundNo, refundAmount, totalAmount, reason, notifyUrl));
    }

    /** 查询微信退款，供订单服务核对金额和原订单。 */
    @GetMapping("/wechat/refund/query")
    public Result<Map<String, Object>> queryWeChatRefund(@RequestParam("tenantId") Long tenantId,
                                                         @RequestParam("outRefundNo") String outRefundNo) {
        return Result.success(weChatPayService.queryRefund(tenantId, outRefundNo));
    }

    /** 用该租户密钥验签并解密微信支付通知。 */
    @PostMapping("/wechat/parse-notify")
    public Result<PaymentNotifyResult> parseWeChatNotify(@RequestBody WeChatNotifyParseDTO dto) {
        return Result.success(weChatPayService.parseAndVerifyNotify(
                dto.getTenantId(), dto.getRequestBody(), dto.getSignature(), dto.getTimestamp(), dto.getNonce(), dto.getSerial()));
    }

    /** 用该租户密钥验签并解密微信退款通知。 */
    @PostMapping("/wechat/parse-refund-notify")
    public Result<PaymentRefundNotifyResult> parseWeChatRefundNotify(@RequestBody WeChatNotifyParseDTO dto) {
        return Result.success(weChatPayService.parseRefundNotify(
                dto.getTenantId(), dto.getRequestBody(), dto.getSignature(), dto.getTimestamp(), dto.getNonce(), dto.getSerial()));
    }

    /** 支付宝手机网站支付。 */
    @PostMapping("/alipay/wap")
    public Result<String> createAlipayWapPay(@RequestBody AlipayTradeWapPayRequestParams param) {
        return Result.success("创建成功", alipayService.createWapPay(param));
    }

    /** 支付宝电脑网站支付。 */
    @PostMapping("/alipay/page")
    public Result<String> createAlipayPagePay(@RequestBody AlipayPayTradeParam param) {
        return Result.success(alipayService.createPagePay(param));
    }

    /** 支付宝当面付预创建。 */
    @PostMapping("/alipay/precreate")
    public Result<String> createAlipayPrecreatePay(@RequestBody AlipayPayTradeParam param) {
        return Result.success(alipayService.createPrecreatePay(param));
    }

    /** 支付宝条码（被扫）支付。 */
    @PostMapping("/alipay/barcode")
    public Result<Map<String, Object>> createAlipayBarCodePay(@RequestBody AlipayBarCodePayParam param) {
        return Result.success(alipayService.createBarCodePay(param.getTenantId(), param.getOutTradeNo(),
                param.getSubject(), param.getTotalAmount(), param.getAuthCode()));
    }

    /** 撤销支付宝交易（条码支付轮询超时使用）。 */
    @PostMapping("/alipay/cancel")
    public Result<Map<String, Object>> cancelAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                         @RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(alipayService.cancelTrade(tenantId, outTradeNo));
    }

    /** 按租户应用查询支付宝订单。 */
    @GetMapping("/alipay/query")
    public Result<Map<String, Object>> queryAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                        @RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(alipayService.queryTrade(tenantId, outTradeNo));
    }

    /** 按租户应用关闭支付宝订单。 */
    @PostMapping("/alipay/close")
    public Result<Map<String, Object>> closeAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                        @RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(alipayService.closeTrade(tenantId, outTradeNo));
    }

    /** 发起支付宝退款。outRequestNo 必须由订单服务生成。 */
    @PostMapping("/alipay/refund")
    public Result<Map<String, Object>> refundAlipayTrade(@RequestParam("tenantId") Long tenantId,
                                                          @RequestParam("outTradeNo") String outTradeNo,
                                                          @RequestParam("outRequestNo") String outRequestNo,
                                                          @RequestParam("refundAmount") String refundAmount,
                                                          @RequestParam(value = "refundReason", required = false) String refundReason) {
        return Result.success(alipayService.refundTrade(tenantId, outTradeNo, refundAmount, refundReason, outRequestNo));
    }

    /** 验签支付宝支付或退款通知。 */
    @PostMapping("/alipay/verify-notify")
    public Result<Boolean> verifyAlipayNotify(@RequestParam("tenantId") Long tenantId,
                                              @RequestBody Map<String, String> params) {
        return Result.success(alipayService.verifyNotify(tenantId, params));
    }

    /** 验签并整理支付宝退款通知。 */
    @PostMapping("/alipay/parse-refund-notify")
    public Result<PaymentRefundNotifyResult> parseAlipayRefundNotify(@RequestParam("tenantId") Long tenantId,
                                                                     @RequestBody Map<String, String> params) {
        return Result.success(alipayService.parseRefundNotify(tenantId, params));
    }
}
