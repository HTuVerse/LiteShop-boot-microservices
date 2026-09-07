package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.params.WeChatPayTradeParam;
import org.hongtu.service.WeChatPayService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 微信支付控制器
 * 
 * @author Ht
 * @since 2025-10-31
 */
@Slf4j
@RestController
@RequestMapping("/pay/wechat")
public class WeChatPayController {

    @Resource
    private WeChatPayService weChatPayService;

    /**
     * 创建小程序支付（JSAPI）
     *
     * @param outTradeNo  商户订单号
     * @param description 订单描述
     * @param totalAmount 订单金额（单位：元）
     * @param openid      用户openid
     * @param attach      附加数据
     * @return 支付参数（返回给小程序用于调起支付）
     */
    @PostMapping("/jsapi")
    public Result<Map<String, Object>> createJsapiPay(
            @RequestParam String outTradeNo,
            @RequestParam String description,
            @RequestParam BigDecimal totalAmount,
            @RequestParam String openid,
            @RequestParam(required = false) String attach) {

        log.info("创建微信小程序支付，订单号：{}，金额：{}，openid：{}", outTradeNo, totalAmount, openid);

        try {
            WeChatPayTradeParam param = WeChatPayTradeParam.builder()
                    .outTradeNo(outTradeNo)
                    .description(description)
                    .totalAmount(totalAmount)
                    .openid(openid)
                    .attach(attach)
                    .tradeType("JSAPI")
                    .build();

            Map<String, Object> payParams = weChatPayService.createJsapiPay(param);
            return Result.success("创建支付成功", payParams);
        } catch (Exception e) {
            log.error("创建微信小程序支付失败", e);
            return Result.error("创建支付失败：" + e.getMessage());
        }
    }

    /**
     * 创建Native支付（扫码支付）
     *
     * @param outTradeNo  商户订单号
     * @param description 订单描述
     * @param totalAmount 订单金额
     * @param attach      附加数据
     * @return 二维码链接
     */
    @PostMapping("/native")
    public Result<String> createNativePay(
            @RequestParam String outTradeNo,
            @RequestParam String description,
            @RequestParam BigDecimal totalAmount,
            @RequestParam(required = false) String attach) {

        log.info("创建微信Native支付，订单号：{}，金额：{}", outTradeNo, totalAmount);

        try {
            WeChatPayTradeParam param = WeChatPayTradeParam.builder()
                    .outTradeNo(outTradeNo)
                    .description(description)
                    .totalAmount(totalAmount)
                    .attach(attach)
                    .tradeType("NATIVE")
                    .build();

            String codeUrl = weChatPayService.createNativePay(param);
            return Result.success("创建支付成功", codeUrl);
        } catch (Exception e) {
            log.error("创建微信Native支付失败", e);
            return Result.error("创建支付失败：" + e.getMessage());
        }
    }

    /**
     * 查询订单
     *
     * @param outTradeNo 商户订单号
     * @return 订单信息
     */
    @GetMapping("/query")
    public Result<Map<String, Object>> queryOrder(@RequestParam String outTradeNo) {
        log.info("查询微信支付订单，订单号：{}", outTradeNo);
        
        try {
            Map<String, Object> result = weChatPayService.queryTrade(outTradeNo);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("查询微信支付订单失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 关闭订单
     *
     * @param outTradeNo 商户订单号
     * @return 关闭结果
     */
    @PostMapping("/close")
    public Result<Map<String, Object>> closeOrder(@RequestParam String outTradeNo) {
        log.info("关闭微信支付订单，订单号：{}", outTradeNo);
        
        try {
            Map<String, Object> result = weChatPayService.closeTrade(outTradeNo);
            return Result.success("关闭成功", result);
        } catch (Exception e) {
            log.error("关闭微信支付订单失败", e);
            return Result.error("关闭失败：" + e.getMessage());
        }
    }

    /**
     * 申请退款
     *
     * @param outTradeNo   商户订单号
     * @param outRefundNo  商户退款单号
     * @param refundAmount 退款金额（单位：分）
     * @param totalAmount  订单总金额（单位：分）
     * @param reason       退款原因
     * @return 退款结果
     */
    @PostMapping("/refund")
    public Result<Map<String, Object>> refund(
            @RequestParam String outTradeNo,
            @RequestParam String outRefundNo,
            @RequestParam String refundAmount,
            @RequestParam String totalAmount,
            @RequestParam(required = false) String reason) {

        log.info("微信支付退款，订单号：{}，退款单号：{}，退款金额：{}", outTradeNo, outRefundNo, refundAmount);
        
        try {
            Map<String, Object> result = weChatPayService.refund(
                    outTradeNo, outRefundNo, refundAmount, totalAmount, reason);
            return Result.success("退款申请成功", result);
        } catch (Exception e) {
            log.error("微信支付退款失败", e);
            return Result.error("退款失败：" + e.getMessage());
        }
    }

    /**
     * 查询退款
     *
     * @param outRefundNo 商户退款单号
     * @return 退款信息
     */
    @GetMapping("/refund/query")
    public Result<Map<String, Object>> queryRefund(@RequestParam String outRefundNo) {
        log.info("查询微信支付退款，退款单号：{}", outRefundNo);
        
        try {
            Map<String, Object> result = weChatPayService.queryRefund(outRefundNo);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("查询微信支付退款失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 微信退款回调通知
     *
     * @param request 请求对象
     * @return 处理结果
     */
    @PostMapping("/notify/refund")
    @SaIgnore
    public String refundNotify(HttpServletRequest request, @RequestBody String requestBody) {
        log.info("接收到微信退款回调通知");
        
        try {
            String signature = request.getHeader("Wechatpay-Signature");
            String timestamp = request.getHeader("Wechatpay-Timestamp");
            String nonce = request.getHeader("Wechatpay-Nonce");
            String serial = request.getHeader("Wechatpay-Serial");

            return weChatPayService.handleRefundNotify(requestBody, signature, timestamp, nonce, serial);
        } catch (Exception e) {
            log.error("处理微信退款回调通知失败", e);
            return "{\"code\": \"FAIL\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }
}

