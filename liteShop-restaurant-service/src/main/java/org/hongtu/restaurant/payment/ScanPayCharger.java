package org.hongtu.restaurant.payment;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.PayFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.params.AlipayBarCodePayParam;
import org.hongtu.params.WeChatMicroPayParam;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 付款码（被扫）扣款编排：发起扣款，用户支付中则轮询查单，超时则撤单。
 * 金额由调用方按服务端重算结果传入，这里不再信任任何前端金额。
 */
@Slf4j
@Component
public class ScanPayCharger {

    /** 轮询最大次数与间隔，覆盖顾客输入密码的常见时长。 */
    static final int MAX_POLLS = 6;
    static final long POLL_INTERVAL_MS = 2000L;

    private final PayFeignClient payFeignClient;
    private final Sleeper sleeper;

    /** 轮询等待抽象，便于测试替换为无等待实现。 */
    public interface Sleeper {
        void sleep(long millis);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ScanPayCharger(PayFeignClient payFeignClient) {
        this(payFeignClient, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** 允许注入自定义等待实现，主要用于测试。 */
    public ScanPayCharger(PayFeignClient payFeignClient, Sleeper sleeper) {
        this.payFeignClient = payFeignClient;
        this.sleeper = sleeper;
    }

    /**
     * 发起付款码扣款。返回已支付或未支付（未支付时已尽力撤单）。
     */
    public ScanPayOutcome charge(PaymentTypeEnum channel, Long tenantId, String outTradeNo,
                                 String subject, BigDecimal amountYuan, String authCode) {
        Map<String, Object> created = create(channel, tenantId, outTradeNo, subject, amountYuan, authCode);
        String state = String.valueOf(created.get("state"));
        if ("SUCCESS".equals(state)) {
            return ScanPayOutcome.paid(transactionId(channel, created));
        }
        if (!isWaiting(state)) {
            return ScanPayOutcome.unpaid(message(created, "顾客付款码支付失败"));
        }
        // 用户支付中：轮询查单，支付成功即落袋，明确失败即结束。
        for (int i = 0; i < MAX_POLLS; i++) {
            sleeper.sleep(POLL_INTERVAL_MS);
            Map<String, Object> queried = query(channel, tenantId, outTradeNo);
            String queryState = queryState(channel, queried);
            if ("SUCCESS".equals(queryState)) {
                return ScanPayOutcome.paid(transactionId(channel, queried));
            }
            if ("FAIL".equals(queryState)) {
                break;
            }
        }
        // 超时或失败：撤单，避免顾客稍后补付造成资损。
        reverseOrCancel(channel, tenantId, outTradeNo);
        return ScanPayOutcome.unpaid("顾客未在规定时间内完成支付，已撤销交易");
    }

    private Map<String, Object> create(PaymentTypeEnum channel, Long tenantId, String outTradeNo,
                                       String subject, BigDecimal amountYuan, String authCode) {
        if (channel == PaymentTypeEnum.WECHAT) {
            return FeignResultUtil.unwrap(payFeignClient.createWeChatMicroPay(WeChatMicroPayParam.builder()
                    .tenantId(tenantId).outTradeNo(outTradeNo).description(subject)
                    .totalAmount(amountYuan).authCode(authCode).build()));
        }
        return FeignResultUtil.unwrap(payFeignClient.createAlipayBarCodePay(AlipayBarCodePayParam.builder()
                .tenantId(tenantId).outTradeNo(outTradeNo).subject(subject)
                .totalAmount(amountYuan).authCode(authCode).build()));
    }

    private Map<String, Object> query(PaymentTypeEnum channel, Long tenantId, String outTradeNo) {
        return channel == PaymentTypeEnum.WECHAT
                ? FeignResultUtil.unwrap(payFeignClient.queryWeChatMicroPay(tenantId, outTradeNo))
                : FeignResultUtil.unwrap(payFeignClient.queryAlipayTrade(tenantId, outTradeNo));
    }

    /** 微信查单直接返回 state；支付宝查单按 tradeStatus 归一。查单异常按等待处理继续轮询。 */
    private String queryState(PaymentTypeEnum channel, Map<String, Object> queried) {
        if (queried == null) {
            return "WAITING";
        }
        if (channel == PaymentTypeEnum.WECHAT) {
            String state = String.valueOf(queried.get("state"));
            return "USERPAYING".equals(state) ? "WAITING" : state;
        }
        String tradeStatus = String.valueOf(queried.get("tradeStatus"));
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            return "SUCCESS";
        }
        if ("TRADE_CLOSED".equals(tradeStatus)) {
            return "FAIL";
        }
        return "WAITING";
    }

    private void reverseOrCancel(PaymentTypeEnum channel, Long tenantId, String outTradeNo) {
        try {
            if (channel == PaymentTypeEnum.WECHAT) {
                FeignResultUtil.unwrap(payFeignClient.reverseWeChatMicroPay(tenantId, outTradeNo));
            } else {
                FeignResultUtil.unwrap(payFeignClient.cancelAlipayTrade(tenantId, outTradeNo));
            }
        } catch (RuntimeException exception) {
            log.error("付款码撤单失败, channel={}, tenantId={}, outTradeNo={}", channel, tenantId, outTradeNo, exception);
        }
    }

    private boolean isWaiting(String state) {
        return "USERPAYING".equals(state) || "WAITING".equals(state);
    }

    private String transactionId(PaymentTypeEnum channel, Map<String, Object> result) {
        Object value = channel == PaymentTypeEnum.WECHAT ? result.get("transactionId") : result.get("tradeNo");
        return value == null ? null : value.toString();
    }

    private String message(Map<String, Object> result, String fallback) {
        Object msg = result == null ? null : result.get("msg");
        return msg != null && StrUtil.isNotBlank(msg.toString()) ? msg.toString() : fallback;
    }
}
