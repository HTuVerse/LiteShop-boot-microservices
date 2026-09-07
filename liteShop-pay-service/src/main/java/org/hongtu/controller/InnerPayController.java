package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
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
 * 支付服务内部接口（仅供其他微服务通过 Feign 调用）
 * 替代原单体中 restaurant 模块对 WeChatPayService / AlipayService 的直接注入
 * 外部经网关访问 /inner/** 会被全局过滤器拦截
 */
@Slf4j
@RestController
@RequestMapping("/inner/pay")
@SaIgnore
public class InnerPayController {

    @Resource
    private WeChatPayService weChatPayService;

    @Resource
    private AlipayService alipayService;

    // ==================== 微信支付 ====================

    /**
     * 微信 JSAPI 预下单
     */
    @PostMapping("/wechat/jsapi")
    public Result<Map<String, Object>> createWeChatJsapiPay(@RequestBody WeChatPayTradeParam param) {
        return Result.success(weChatPayService.createJsapiPay(param));
    }

    /**
     * 微信查单
     */
    @GetMapping("/wechat/query")
    public Result<Map<String, Object>> queryWeChatTrade(@RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(weChatPayService.queryTrade(outTradeNo));
    }

    /**
     * 微信关单
     */
    @PostMapping("/wechat/close")
    public Result<Map<String, Object>> closeWeChatTrade(@RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(weChatPayService.closeTrade(outTradeNo));
    }

    /**
     * 微信回调验签解密
     */
    @PostMapping("/wechat/parse-notify")
    public Result<PaymentNotifyResult> parseWeChatNotify(@RequestBody WeChatNotifyParseDTO dto) {
        PaymentNotifyResult result = weChatPayService.parseAndVerifyNotify(
                dto.getRequestBody(), dto.getSignature(), dto.getTimestamp(), dto.getNonce(), dto.getSerial());
        return Result.success(result);
    }

    // ==================== 支付宝 ====================

    /**
     * 支付宝 WAP 支付，返回支付表单 HTML
     */
    @PostMapping("/alipay/wap")
    public Result<String> createAlipayWapPay(@RequestBody AlipayTradeWapPayRequestParams param) {
        return Result.success("创建成功", alipayService.createWapPay(param));
    }

    /**
     * 支付宝查单
     */
    @GetMapping("/alipay/query")
    public Result<Map<String, Object>> queryAlipayTrade(@RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(alipayService.queryTrade(outTradeNo));
    }

    /**
     * 支付宝关单
     */
    @PostMapping("/alipay/close")
    public Result<Map<String, Object>> closeAlipayTrade(@RequestParam("outTradeNo") String outTradeNo) {
        return Result.success(alipayService.closeTrade(outTradeNo));
    }

    @PostMapping("/alipay/verify-notify")
    public Result<Boolean> verifyAlipayNotify(@RequestBody Map<String, String> params) {
        return Result.success(alipayService.verifyNotify(params));
    }
}
