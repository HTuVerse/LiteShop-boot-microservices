package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.api.client.RestaurantPaymentFeignClient;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.service.AlipayService;
import org.hongtu.service.WeChatPayService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** 渠道公开回调入口：验签、解密后转交业务服务处理订单。 */
@Slf4j
@RestController
@RequestMapping({"", "/restaurant-order"})
@SaIgnore
@RequiredArgsConstructor
public class PaymentNotifyController {
    private static final String WECHAT_SUCCESS = "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    private static final String WECHAT_FAILURE = "{\"code\":\"FAIL\",\"message\":\"处理失败\"}";

    private final WeChatPayService weChatPayService;
    private final AlipayService alipayService;
    private final RestaurantPaymentFeignClient restaurantPayment;

    /** 微信支付通知，验签与商户核对后交由餐厅服务入账。 */
    @PostMapping("/wechatPay/notify/{tenantId}")
    public String wechatPaid(@PathVariable Long tenantId, HttpServletRequest request,
                             @RequestBody String body) {
        try {
            PaymentNotifyResult notice = weChatPayService.parseAndVerifyNotify(tenantId, body,
                    header(request, "Wechatpay-Signature"), header(request, "Wechatpay-Timestamp"),
                    header(request, "Wechatpay-Nonce"), header(request, "Wechatpay-Serial"));
            if (!Boolean.TRUE.equals(notice.getIsSuccess())) {
                return WECHAT_FAILURE;
            }
            requireHandled(FeignResultUtil.unwrap(restaurantPayment.paid(tenantId, notice)));
            return WECHAT_SUCCESS;
        } catch (Exception exception) {
            log.error("微信支付通知处理失败, tenantId={}", tenantId, exception);
            return WECHAT_FAILURE;
        }
    }

    /** 微信退款通知，验签与商户核对后交由餐厅服务更新退款单。 */
    @PostMapping("/wechatPay/refund-notify/{tenantId}")
    public String wechatRefunded(@PathVariable Long tenantId, HttpServletRequest request,
                                 @RequestBody String body) {
        try {
            PaymentRefundNotifyResult notice = weChatPayService.parseRefundNotify(tenantId, body,
                    header(request, "Wechatpay-Signature"), header(request, "Wechatpay-Timestamp"),
                    header(request, "Wechatpay-Nonce"), header(request, "Wechatpay-Serial"));
            requireHandled(FeignResultUtil.unwrap(restaurantPayment.refunded(tenantId, notice)));
            return WECHAT_SUCCESS;
        } catch (Exception exception) {
            log.error("微信退款通知处理失败, tenantId={}", tenantId, exception);
            return WECHAT_FAILURE;
        }
    }

    /** 支付宝支付通知，验签后交由餐厅服务核对订单与金额。 */
    @PostMapping("/alipayPay/notify/{tenantId}")
    public String alipayPaid(@PathVariable Long tenantId, HttpServletRequest request) {
        try {
            Map<String, String> params = params(request);
            if (!alipayService.verifyNotify(tenantId, params)) {
                return "failure";
            }
            String status = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) {
                return "success";
            }
            PaymentNotifyResult notice = PaymentNotifyResult.builder()
                    .outTradeNo(params.get("out_trade_no"))
                    .transactionId(params.get("trade_no"))
                    .totalAmount(new BigDecimal(params.get("total_amount")).movePointRight(2).intValueExact())
                    .currency("CNY")
                    .attach(params.get("passback_params"))
                    .paymentType("ALIPAY")
                    .isSuccess(true)
                    .build();
            requireHandled(FeignResultUtil.unwrap(restaurantPayment.paid(tenantId, notice)));
            return "success";
        } catch (Exception exception) {
            log.error("支付宝支付通知处理失败, tenantId={}", tenantId, exception);
            return "failure";
        }
    }

    /** 支付宝退款通知，验签后交由餐厅服务更新退款单。 */
    @PostMapping("/alipayPay/refund-notify/{tenantId}")
    public String alipayRefunded(@PathVariable Long tenantId, HttpServletRequest request) {
        try {
            PaymentRefundNotifyResult notice = alipayService.parseRefundNotify(tenantId, params(request));
            requireHandled(FeignResultUtil.unwrap(restaurantPayment.refunded(tenantId, notice)));
            return "success";
        } catch (Exception exception) {
            log.error("支付宝退款通知处理失败, tenantId={}", tenantId, exception);
            return "failure";
        }
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new BusinessException("微信支付通知缺少签名请求头");
        }
        return value;
    }

    private Map<String, String> params(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                result.put(key, values[0]);
            }
        });
        return result;
    }

    private void requireHandled(Boolean handled) {
        if (!Boolean.TRUE.equals(handled)) {
            throw new BusinessException("订单服务未确认支付通知");
        }
    }
}
