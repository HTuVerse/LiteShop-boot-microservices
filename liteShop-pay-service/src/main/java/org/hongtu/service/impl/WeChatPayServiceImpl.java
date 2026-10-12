package org.hongtu.service.impl;

import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.exception.BusinessException;
import org.hongtu.params.WeChatPayTradeParam;
import org.hongtu.service.TenantPaymentClientFactory;
import org.hongtu.service.TenantPaymentClientFactory.WeChatClients;
import org.hongtu.service.WeChatPayService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用租户自己的微信商户完成收单和退款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatPayServiceImpl implements WeChatPayService {
    private final TenantPaymentClientFactory clients;

    @Override
    public Map<String, Object> createJsapiPay(WeChatPayTradeParam param) {
        if (param == null || param.getTenantId() == null) {
            throw new BusinessException("微信支付命令缺少租户");
        }
        if (!StringUtils.hasText(param.getOutTradeNo()) || !StringUtils.hasText(param.getDescription())
                || !StringUtils.hasText(param.getOpenid())
                || !StringUtils.hasText(param.getNotifyUrl()) || param.getTotalAmount() == null
                || param.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("微信支付参数不完整");
        }
        WeChatClients merchant = clients.wechat(param.getTenantId());
        try {
            com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request =
                    new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
            request.setAppid(merchant.appId());
            request.setMchid(merchant.mchId());
            request.setDescription(param.getDescription());
            request.setOutTradeNo(param.getOutTradeNo());
            request.setNotifyUrl(param.getNotifyUrl());
            com.wechat.pay.java.service.payments.jsapi.model.Amount amount =
                    new com.wechat.pay.java.service.payments.jsapi.model.Amount();
            amount.setTotal(param.getTotalAmount().movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).intValueExact());
            request.setAmount(amount);
            com.wechat.pay.java.service.payments.jsapi.model.Payer payer =
                    new com.wechat.pay.java.service.payments.jsapi.model.Payer();
            payer.setOpenid(param.getOpenid());
            request.setPayer(payer);
            if (param.getAttach() != null) {
                request.setAttach(param.getAttach());
            }
            if (param.getTimeExpire() != null && param.getTimeExpire() > 0) {
                request.setTimeExpire(java.time.OffsetDateTime.now().plusMinutes(param.getTimeExpire())
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            var paymentResponse = merchant.pay().prepayWithRequestPayment(request);
            Map<String, Object> payParams = new HashMap<>();
            payParams.put("timeStamp", paymentResponse.getTimeStamp());
            payParams.put("nonceStr", paymentResponse.getNonceStr());
            payParams.put("package", paymentResponse.getPackageVal());
            payParams.put("signType", paymentResponse.getSignType());
            payParams.put("paySign", paymentResponse.getPaySign());
            return payParams;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("创建微信小程序支付失败, tenantId={}", param.getTenantId(), exception);
            throw new BusinessException("创建微信支付失败");
        }
    }

    /** 付款码场景的调用方 IP 占位，仅作参数填充，不参与风控判断。 */
    private static final String MICRO_PAY_SPBILL_IP = "127.0.0.1";

    @Override
    public Map<String, Object> createMicroPay(Long tenantId, String outTradeNo, String description,
                                              BigDecimal totalAmountYuan, String authCode) {
        if (tenantId == null) {
            throw new BusinessException("微信付款码命令缺少租户");
        }
        if (!StringUtils.hasText(outTradeNo) || !StringUtils.hasText(description)
                || !StringUtils.hasText(authCode) || totalAmountYuan == null
                || totalAmountYuan.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("微信付款码支付参数不完整");
        }
        int totalFee;
        try {
            totalFee = totalAmountYuan.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException("微信付款码金额精度非法");
        }
        com.github.binarywang.wxpay.service.WxPayService service = clients.wxMicroPay(tenantId);
        com.github.binarywang.wxpay.bean.request.WxPayMicropayRequest request =
                com.github.binarywang.wxpay.bean.request.WxPayMicropayRequest.newBuilder()
                        .outTradeNo(outTradeNo)
                        .body(description)
                        .totalFee(totalFee)
                        .authCode(authCode)
                        .spbillCreateIp(MICRO_PAY_SPBILL_IP)
                        .build();
        try {
            com.github.binarywang.wxpay.bean.result.WxPayMicropayResult result = service.micropay(request);
            return microState("SUCCESS", result.getTransactionId(), outTradeNo, "支付成功");
        } catch (com.github.binarywang.wxpay.exception.WxPayException exception) {
            String errCode = exception.getErrCode();
            // 这些错误码表示用户还在输密码/系统处理中，需继续轮询查单，不能直接判失败。
            if ("USERPAYING".equals(errCode) || "SYSTEMERROR".equals(errCode) || "BANKERROR".equals(errCode)) {
                return microState("USERPAYING", null, outTradeNo, exception.getErrCodeDes());
            }
            log.warn("微信付款码支付失败, tenantId={}, outTradeNo={}, errCode={}", tenantId, outTradeNo, errCode);
            return microState("FAIL", null, outTradeNo, exception.getErrCodeDes());
        }
    }

    @Override
    public Map<String, Object> queryMicroPay(Long tenantId, String outTradeNo) {
        com.github.binarywang.wxpay.service.WxPayService service = clients.wxMicroPay(tenantId);
        try {
            com.github.binarywang.wxpay.bean.result.WxPayOrderQueryResult result = service.queryOrder(null, outTradeNo);
            String tradeState = result.getTradeState();
            String state;
            if ("SUCCESS".equals(tradeState)) {
                state = "SUCCESS";
            } else if ("USERPAYING".equals(tradeState) || "NOTPAY".equals(tradeState)) {
                state = "USERPAYING";
            } else {
                state = "FAIL";
            }
            return microState(state, result.getTransactionId(), outTradeNo, result.getTradeStateDesc());
        } catch (com.github.binarywang.wxpay.exception.WxPayException exception) {
            log.warn("查询微信付款码订单失败, tenantId={}, outTradeNo={}", tenantId, outTradeNo, exception);
            return microState("FAIL", null, outTradeNo, exception.getErrCodeDes());
        }
    }

    @Override
    public Map<String, Object> reverseMicroPay(Long tenantId, String outTradeNo) {
        com.github.binarywang.wxpay.service.WxPayService service = clients.wxMicroPay(tenantId);
        Map<String, Object> result = new HashMap<>();
        try {
            com.github.binarywang.wxpay.bean.request.WxPayOrderReverseRequest request =
                    new com.github.binarywang.wxpay.bean.request.WxPayOrderReverseRequest();
            request.setOutTradeNo(outTradeNo);
            com.github.binarywang.wxpay.bean.result.WxPayOrderReverseResult reverse = service.reverseOrder(request);
            result.put("success", true);
            result.put("recall", "Y".equalsIgnoreCase(reverse.getIsRecall()));
            return result;
        } catch (com.github.binarywang.wxpay.exception.WxPayException exception) {
            log.error("撤销微信付款码订单失败, tenantId={}, outTradeNo={}", tenantId, outTradeNo, exception);
            // 撤单失败按需要重试处理，交由调用方继续轮询撤销。
            result.put("success", false);
            result.put("recall", true);
            result.put("msg", exception.getErrCodeDes());
            return result;
        }
    }

    private Map<String, Object> microState(String state, String transactionId, String outTradeNo, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("state", state);
        result.put("success", "SUCCESS".equals(state));
        result.put("transactionId", transactionId);
        result.put("outTradeNo", outTradeNo);
        result.put("msg", message);
        return result;
    }

    @Override
    public Map<String, Object> queryTrade(Long tenantId, String outTradeNo) {
        WeChatClients merchant = clients.wechat(tenantId);
        try {
            QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
            request.setMchid(merchant.mchId());
            request.setOutTradeNo(outTradeNo);
            Transaction transaction = merchant.query().queryOrderByOutTradeNo(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("transactionId", transaction.getTransactionId());
            result.put("outTradeNo", transaction.getOutTradeNo());
            result.put("tradeState", transaction.getTradeState() == null ? null : transaction.getTradeState().name());
            result.put("tradeStateDesc", transaction.getTradeStateDesc());
            result.put("successTime", transaction.getSuccessTime());
            if (transaction.getAmount() != null) {
                result.put("total", transaction.getAmount().getTotal());
                result.put("payerTotal", transaction.getAmount().getPayerTotal());
                result.put("currency", transaction.getAmount().getCurrency());
            }
            result.put("mchid", transaction.getMchid());
            result.put("merchantMatched", merchant.mchId().equals(transaction.getMchid()));
            return result;
        } catch (RuntimeException exception) {
            log.error("查询微信支付订单失败, tenantId={}, outTradeNo={}", tenantId, outTradeNo, exception);
            return failure(exception.getMessage());
        }
    }

    @Override
    public Map<String, Object> closeTrade(Long tenantId, String outTradeNo) {
        WeChatClients merchant = clients.wechat(tenantId);
        try {
            CloseOrderRequest request = new CloseOrderRequest();
            request.setMchid(merchant.mchId());
            request.setOutTradeNo(outTradeNo);
            merchant.query().closeOrder(request);
            return outcome("CLOSED", "订单关闭成功");
        } catch (ServiceException exception) {
            String code = exception.getErrorCode();
            if ("ORDERPAID".equals(code)) {
                return outcome("PAID", "订单已支付");
            }
            if ("ORDERCLOSED".equals(code) || "ORDERNOTEXIST".equals(code)) {
                return outcome("CLOSED", "订单已关闭或不存在");
            }
            log.warn("关闭微信支付订单结果不明确, tenantId={}, code={}", tenantId, code);
            return outcome("UNKNOWN", exception.getErrorMessage());
        } catch (RuntimeException exception) {
            log.warn("关闭微信支付订单结果不明确, tenantId={}", tenantId, exception);
            return outcome("UNKNOWN", exception.getMessage());
        }
    }

    @Override
    public Map<String, Object> refund(Long tenantId, String outTradeNo, String outRefundNo,
                                      String refundAmount, String totalAmount, String reason, String notifyUrl) {
        WeChatClients merchant = clients.wechat(tenantId);
        try {
            CreateRequest request = new CreateRequest();
            request.setOutTradeNo(outTradeNo);
            request.setOutRefundNo(outRefundNo);
            request.setReason(reason);
            request.setNotifyUrl(notifyUrl);
            com.wechat.pay.java.service.refund.model.AmountReq amountReq =
                    new com.wechat.pay.java.service.refund.model.AmountReq();
            amountReq.setRefund(Long.parseLong(refundAmount));
            amountReq.setTotal(Long.parseLong(totalAmount));
            amountReq.setCurrency("CNY");
            request.setAmount(amountReq);
            Refund refund = merchant.refund().create(request);
            return refundResult(refund, merchant, true, null);
        } catch (RuntimeException exception) {
            log.error("微信支付退款失败, tenantId={}, outRefundNo={}", tenantId, outRefundNo, exception);
            return failure(exception.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryRefund(Long tenantId, String outRefundNo) {
        WeChatClients merchant = clients.wechat(tenantId);
        try {
            QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
            request.setOutRefundNo(outRefundNo);
            return refundResult(merchant.refund().queryByOutRefundNo(request), merchant, true, null);
        } catch (RuntimeException exception) {
            log.error("查询微信支付退款失败, tenantId={}, outRefundNo={}", tenantId, outRefundNo, exception);
            return failure(exception.getMessage());
        }
    }

    @Override
    public PaymentNotifyResult parseAndVerifyNotify(Long tenantId, String requestBody, String signature,
                                                    String timestamp, String nonce, String serial) {
        WeChatClients merchant = clients.wechat(tenantId);
        Transaction transaction = merchant.parser().parse(request(requestBody, signature, timestamp, nonce, serial), Transaction.class);
        if (!merchant.appId().equals(transaction.getAppid()) || !merchant.mchId().equals(transaction.getMchid())) {
            throw new BusinessException("微信支付回调应用或商户号不匹配");
        }
        return PaymentNotifyResult.builder()
                .outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId())
                .tradeState(transaction.getTradeState().name())
                .tradeStateDesc(transaction.getTradeStateDesc())
                .successTime(transaction.getSuccessTime())
                .totalAmount(transaction.getAmount() != null ? transaction.getAmount().getTotal() : null)
                .payerAmount(transaction.getAmount() != null ? transaction.getAmount().getPayerTotal() : null)
                .currency(transaction.getAmount() != null ? transaction.getAmount().getCurrency() : "CNY")
                .attach(transaction.getAttach())
                .payerOpenid(transaction.getPayer() != null ? transaction.getPayer().getOpenid() : null)
                .isSuccess(Transaction.TradeStateEnum.SUCCESS.equals(transaction.getTradeState()))
                .paymentType("WECHAT")
                .build();
    }

    @Override
    public PaymentRefundNotifyResult parseRefundNotify(Long tenantId, String requestBody, String signature,
                                                       String timestamp, String nonce, String serial) {
        WeChatClients merchant = clients.wechat(tenantId);
        Refund refund = merchant.parser().parse(request(requestBody, signature, timestamp, nonce, serial), Refund.class);
        PaymentRefundNotifyResult result = new PaymentRefundNotifyResult();
        result.setOutRefundNo(refund.getOutRefundNo());
        result.setOutTradeNo(refund.getOutTradeNo());
        result.setChannelRefundId(refund.getRefundId());
        result.setStatus(refund.getStatus() == null ? null : refund.getStatus().name());
        if (refund.getAmount() != null && refund.getAmount().getRefund() != null) {
            result.setRefundAmount(BigDecimal.valueOf(refund.getAmount().getRefund(), 2));
        }
        result.setAppId(merchant.appId());
        result.setMchId(merchant.mchId());
        result.setMerchantMatched(true);
        return result;
    }

    private RequestParam request(String body, String signature, String timestamp, String nonce, String serial) {
        return new RequestParam.Builder()
                .serialNumber(serial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(body)
                .build();
    }

    private Map<String, Object> refundResult(Refund refund, WeChatClients merchant, boolean success, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("msg", message);
        if (refund == null) {
            return result;
        }
        result.put("refundId", refund.getRefundId());
        result.put("outRefundNo", refund.getOutRefundNo());
        result.put("outTradeNo", refund.getOutTradeNo());
        result.put("status", refund.getStatus() == null ? null : refund.getStatus().name());
        result.put("successTime", refund.getSuccessTime());
        if (refund.getAmount() != null) {
            result.put("refund", refund.getAmount().getRefund());
            result.put("total", refund.getAmount().getTotal());
        }
        result.put("mchid", merchant.mchId());
        result.put("merchantMatched", true);
        return result;
    }

    private Map<String, Object> outcome(String outcome, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("outcome", outcome);
        result.put("msg", message);
        return result;
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("outcome", "UNKNOWN");
        result.put("msg", message);
        return result;
    }
}
