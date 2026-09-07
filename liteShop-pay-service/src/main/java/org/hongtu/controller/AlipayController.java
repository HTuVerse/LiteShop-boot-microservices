package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.alipay.api.request.AlipayTradePagePayRequest;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.params.AlipayTradePagePayRequestParams;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.service.AlipayService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付控制器
 */
@Slf4j
@RestController
@RequestMapping("/pay/alipay")
@RequiredArgsConstructor
public class  AlipayController {

    @Resource
    AlipayService alipayService;

    /**
     * 电脑网站支付
     * 参考文档：https://opendocs.alipay.com/open/028r8t?scene=22
     *
     * @param request  支付请求参数
     * @param response 响应对象
     * @throws IOException IO异常
     */
    @PostMapping("/pagePay")
    @SaIgnore
    public void pagePay(@RequestBody AlipayTradePagePayRequestParams request, HttpServletResponse response) throws IOException {
        // 直接调用 Service 层，不再需要参数转换
        String result = alipayService.createPagePay(request);

        response.setContentType("text/html;charset=UTF-8");
        if (result != null) {
            log.info("支付宝电脑网站支付表单生成成功，订单号：{}", request.getOutTradeNo());
            response.getWriter().write(result);
        } else {
            log.error("支付宝电脑网站支付表单生成失败，订单号：{}", request.getOutTradeNo());
            response.getWriter().write("<html><body><h3>支付宝支付表单生成失败，请稍后重试</h3></body></html>");
        }
        response.getWriter().flush();
        response.getWriter().close();
    }

    /**
     * 手机网站支付
     * 参考文档：https://opendocs.alipay.com/open/02ivbs?scene=21
     *
     * @param request  支付请求参数
     * @param response 响应对象
     * @throws IOException IO异常
     */
    @PostMapping("/wapPay")
    @SaIgnore
    public void wapPay(@RequestBody AlipayTradeWapPayRequestParams request, HttpServletResponse response) throws IOException {
        // 直接调用 Service 层，不再需要参数转换
        String result = alipayService.createWapPay(request);
        response.setContentType("text/html;charset=UTF-8");
        if (result != null) {
            response.getWriter().write(result);
        } else {
            response.getWriter().write("<html><body><h3>支付宝支付表单生成失败，请稍后重试</h3></body></html>");
        }
        response.getWriter().flush();
        response.getWriter().close();
    }

    /**
     * 交易查询
     *
     * @param outTradeNo 商户订单号
     * @return 查询结果
     */
    @GetMapping("/query")
    public Result<Map<String, Object>> query(@RequestParam String outTradeNo) {
        log.info("查询支付宝交易，订单号：{}", outTradeNo);
        Map<String, Object> result = alipayService.queryTrade(outTradeNo);
        return Result.success(result);
    }

    /**
     * 交易退款
     *
     * @param outTradeNo   商户订单号
     * @param refundAmount 退款金额
     * @param refundReason 退款原因
     * @return 退款结果
     */
    @PostMapping("/refund")
    public Result<Map<String, Object>> refund(
            @RequestParam String outTradeNo,
            @RequestParam String refundAmount,
            @RequestParam(required = false) String refundReason) {

        log.info("支付宝交易退款，订单号：{}，退款金额：{}", outTradeNo, refundAmount);
        Map<String, Object> result = alipayService.refundTrade(outTradeNo, refundAmount, refundReason);
        return Result.success(result);
    }

    /**
     * 交易关闭
     *
     * @param outTradeNo 商户订单号
     * @return 关闭结果
     */
    @PostMapping("/close")
    public Result<Map<String, Object>> close(@RequestParam String outTradeNo) {
        log.info("关闭支付宝交易，订单号：{}", outTradeNo);
        Map<String, Object> result = alipayService.closeTrade(outTradeNo);
        return Result.success(result);
    }

    /**
     * 支付宝异步通知
     *
     * @param request 请求对象
     * @return 处理结果
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        log.info("接收到支付宝异步通知");
        return alipayService.notifyProcess(request);
    }

    /**
     * 支付宝同步回调
     *
     * @param request 请求对象
     * @return 处理结果
     */
    @GetMapping("/return")
    public String returnUrl(HttpServletRequest request) {
        log.info("接收到支付宝同步回调");
        return alipayService.returnProcess(request);
    }
} 