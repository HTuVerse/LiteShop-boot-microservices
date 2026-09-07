package org.hongtu.api.client;

import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.params.WeChatPayTradeParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 支付服务 Feign 客户端
 * 对应 pay-service 的 /inner/pay/** 内部接口
 */
@FeignClient(name = "liteShop-pay-service", contextId = "payFeignClient")
public interface PayFeignClient {

    // ==================== 微信支付 ====================

    /**
     * 微信 JSAPI 预下单（替代原 restaurant 直接调 WeChatPayService.createJsapiPay）
     */
    @PostMapping("/inner/pay/wechat/jsapi")
    Result<Map<String, Object>> createWeChatJsapiPay(@RequestBody WeChatPayTradeParam param);

    /**
     * 微信查单
     */
    @GetMapping("/inner/pay/wechat/query")
    Result<Map<String, Object>> queryWeChatTrade(@RequestParam("outTradeNo") String outTradeNo);

    /**
     * 微信关单
     */
    @PostMapping("/inner/pay/wechat/close")
    Result<Map<String, Object>> closeWeChatTrade(@RequestParam("outTradeNo") String outTradeNo);

    /**
     * 微信回调验签解密（替代原 WeChatPayService.parseAndVerifyNotify）
     */
    @PostMapping("/inner/pay/wechat/parse-notify")
    Result<PaymentNotifyResult> parseWeChatNotify(@RequestBody WeChatNotifyParseDTO dto);

    // ==================== 支付宝 ====================

    /**
     * 支付宝 WAP 支付，返回支付表单 HTML（替代原 AlipayService.createWapPay）
     */
    @PostMapping("/inner/pay/alipay/wap")
    Result<String> createAlipayWapPay(@RequestBody AlipayTradeWapPayRequestParams param);

    /**
     * 支付宝查单
     */
    @GetMapping("/inner/pay/alipay/query")
    Result<Map<String, Object>> queryAlipayTrade(@RequestParam("outTradeNo") String outTradeNo);

    /**
     * 支付宝关单
     */
    @PostMapping("/inner/pay/alipay/close")
    Result<Map<String, Object>> closeAlipayTrade(@RequestParam("outTradeNo") String outTradeNo);

    /** 支付宝异步回调验签（回调参数必须原样传递）。 */
    @PostMapping("/inner/pay/alipay/verify-notify")
    Result<Boolean> verifyAlipayNotify(@RequestBody Map<String, String> params);
}
