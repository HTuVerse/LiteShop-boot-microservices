package org.hongtu.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.service.AlipayService;
import org.hongtu.service.TenantPaymentClientFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用租户自己的支付宝应用完成收单和退款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {
    private final TenantPaymentClientFactory clients;

    @Override
    public String createWapPay(AlipayTradeWapPayRequestParams wapPayRequest) {
        if (wapPayRequest == null || wapPayRequest.getTenantId() == null) {
            throw new BusinessException("支付宝支付命令缺少租户");
        }
        if (!StringUtils.hasText(wapPayRequest.getOutTradeNo())
                || !StringUtils.hasText(wapPayRequest.getSubject())
                || !StringUtils.hasText(wapPayRequest.getNotifyUrl())
                || !StringUtils.hasText(wapPayRequest.getProductCode())
                || wapPayRequest.getTotalAmount() == null
                || wapPayRequest.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("支付宝支付参数不完整");
        }
        var client = clients.alipay(wapPayRequest.getTenantId());
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(wapPayRequest.getNotifyUrl());
        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", wapPayRequest.getOutTradeNo());
        bizContent.put("total_amount", wapPayRequest.getTotalAmount()
                .setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        bizContent.put("subject", wapPayRequest.getSubject());
        bizContent.put("product_code", wapPayRequest.getProductCode());
        put(bizContent, "time_expire", wapPayRequest.getTimeExpire());
        put(bizContent, "passback_params", wapPayRequest.getPassbackParams());
        put(bizContent, "quit_url", wapPayRequest.getQuitUrl());
        request.setBizContent(JSON.toJSONString(bizContent));
        try {
            AlipayTradeWapPayResponse response = client.pageExecute(request);
            if (!response.isSuccess()) {
                throw new BusinessException("创建支付宝支付失败");
            }
            return response.getBody();
        } catch (AlipayApiException exception) {
            log.error("创建支付宝支付失败, tenantId={}", wapPayRequest.getTenantId(), exception);
            throw new BusinessException("创建支付宝支付失败");
        }
    }

    @Override
    public Map<String, Object> createBarCodePay(Long tenantId, String outTradeNo, String subject,
                                                BigDecimal totalAmountYuan, String authCode) {
        if (tenantId == null) {
            throw new BusinessException("支付宝条码支付命令缺少租户");
        }
        if (!StringUtils.hasText(outTradeNo) || !StringUtils.hasText(subject)
                || !StringUtils.hasText(authCode) || totalAmountYuan == null
                || totalAmountYuan.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("支付宝条码支付参数不完整");
        }
        String amount;
        try {
            amount = totalAmountYuan.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException exception) {
            throw new BusinessException("支付宝条码支付金额精度非法");
        }
        com.alipay.api.request.AlipayTradePayRequest request = new com.alipay.api.request.AlipayTradePayRequest();
        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("scene", "bar_code");
        bizContent.put("auth_code", authCode);
        bizContent.put("subject", subject);
        bizContent.put("total_amount", amount);
        request.setBizContent(JSON.toJSONString(bizContent));
        try {
            com.alipay.api.response.AlipayTradePayResponse response = clients.alipay(tenantId).execute(request);
            Map<String, Object> result = base(response.isSuccess(), response.getCode(), response.getMsg(),
                    response.getSubCode(), response.getSubMsg());
            // 10000 支付成功；10003 等待用户输入密码，需要轮询查单；其余判失败。
            String state = "10000".equals(response.getCode()) ? "SUCCESS"
                    : ("10003".equals(response.getCode()) ? "WAITING" : "FAIL");
            result.put("state", state);
            result.put("tradeNo", response.getTradeNo());
            result.put("outTradeNo", response.getOutTradeNo());
            return result;
        } catch (AlipayApiException exception) {
            log.error("支付宝条码支付失败, tenantId={}, outTradeNo={}", tenantId, outTradeNo, exception);
            Map<String, Object> result = unknown(exception.getMessage());
            result.put("state", "FAIL");
            return result;
        }
    }

    @Override
    public Map<String, Object> cancelTrade(Long tenantId, String outTradeNo) {
        com.alipay.api.request.AlipayTradeCancelRequest request = new com.alipay.api.request.AlipayTradeCancelRequest();
        request.setBizContent(JSON.toJSONString(Map.of("out_trade_no", outTradeNo)));
        try {
            com.alipay.api.response.AlipayTradeCancelResponse response = clients.alipay(tenantId).execute(request);
            Map<String, Object> result = base(response.isSuccess(), response.getCode(), response.getMsg(),
                    response.getSubCode(), response.getSubMsg());
            result.put("retryFlag", response.getRetryFlag());
            result.put("action", response.getAction());
            return result;
        } catch (AlipayApiException exception) {
            log.error("撤销支付宝交易失败, tenantId={}, outTradeNo={}", tenantId, outTradeNo, exception);
            Map<String, Object> result = unknown(exception.getMessage());
            result.put("retryFlag", "Y");
            return result;
        }
    }

    @Override
    public Map<String, Object> queryTrade(Long tenantId, String outTradeNo) {
        TenantPaymentRuntimeDTO account = clients.alipayAccount(tenantId);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(JSON.toJSONString(Map.of("out_trade_no", outTradeNo)));
        try {
            AlipayTradeQueryResponse response = clients.alipay(tenantId).execute(request);
            Map<String, Object> result = base(response.isSuccess(), response.getCode(), response.getMsg(),
                    response.getSubCode(), response.getSubMsg());
            result.put("tradeStatus", response.getTradeStatus());
            result.put("tradeNo", response.getTradeNo());
            result.put("outTradeNo", response.getOutTradeNo());
            result.put("totalAmount", response.getTotalAmount());
            result.put("currency", "CNY");
            result.put("merchantMatched", response.isSuccess());
            result.put("appId", account.getAppId());
            return result;
        } catch (AlipayApiException exception) {
            log.error("查询支付宝交易失败, tenantId={}", tenantId, exception);
            return unknown(exception.getMessage());
        }
    }

    @Override
    public Map<String, Object> closeTrade(Long tenantId, String outTradeNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizContent(JSON.toJSONString(Map.of("out_trade_no", outTradeNo)));
        try {
            AlipayTradeCloseResponse response = clients.alipay(tenantId).execute(request);
            if (response.isSuccess() || "ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                return outcome("CLOSED", response.getSubMsg());
            }
            if ("ACQ.TRADE_STATUS_ERROR".equals(response.getSubCode())) {
                return outcome("UNKNOWN", response.getSubMsg());
            }
            return outcome("UNKNOWN", response.getSubMsg());
        } catch (AlipayApiException exception) {
            log.warn("关闭支付宝交易结果不明确, tenantId={}", tenantId, exception);
            return unknown(exception.getMessage());
        }
    }

    @Override
    public Map<String, Object> refundTrade(Long tenantId, String outTradeNo, String refundAmount,
                                           String refundReason, String outRequestNo) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("refund_amount", refundAmount);
        bizContent.put("refund_reason", refundReason);
        bizContent.put("out_request_no", outRequestNo);
        request.setBizContent(JSON.toJSONString(bizContent));
        try {
            AlipayTradeRefundResponse response = clients.alipay(tenantId).execute(request);
            Map<String, Object> result = base(response.isSuccess(), response.getCode(), response.getMsg(),
                    response.getSubCode(), response.getSubMsg());
            result.put("tradeNo", response.getTradeNo());
            result.put("outTradeNo", response.getOutTradeNo());
            result.put("refundFee", response.getRefundFee());
            result.put("fundChange", response.getFundChange());
            result.put("status", response.isSuccess() ? "SUCCESS" : "FAILED");
            result.put("merchantMatched", response.isSuccess());
            return result;
        } catch (AlipayApiException exception) {
            log.error("支付宝退款失败, tenantId={}", tenantId, exception);
            Map<String, Object> result = unknown(exception.getMessage());
            result.put("success", false);
            result.put("status", "FAILED");
            return result;
        }
    }

    @Override
    public boolean verifyNotify(Long tenantId, Map<String, String> params) {
        TenantPaymentRuntimeDTO account = clients.alipayAccount(tenantId);
        if (params == null || !account.getAppId().equals(params.get("app_id"))) {
            return false;
        }
        try {
            return AlipaySignature.rsaCheckV1(params, account.getAlipayPublicKey(), "utf-8", "RSA2");
        } catch (AlipayApiException exception) {
            log.warn("支付宝回调验签失败, tenantId={}", tenantId, exception);
            return false;
        }
    }

    @Override
    public PaymentRefundNotifyResult parseRefundNotify(Long tenantId, Map<String, String> params) {
        if (!verifyNotify(tenantId, params)) {
            throw new BusinessException("支付宝退款回调验签失败");
        }
        PaymentRefundNotifyResult result = new PaymentRefundNotifyResult();
        result.setOutTradeNo(params.get("out_trade_no"));
        result.setOutRefundNo(params.get("out_biz_no"));
        result.setChannelRefundId(params.get("trade_no"));
        result.setAppId(params.get("app_id"));
        result.setMerchantMatched(true);
        String refundFee = params.get("refund_fee");
        if (refundFee != null && !refundFee.isBlank()) {
            result.setRefundAmount(new BigDecimal(refundFee));
        }
        result.setStatus("SUCCESS");
        return result;
    }

    private Map<String, Object> base(boolean success, String code, String msg, String subCode, String subMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("code", code);
        result.put("msg", msg);
        result.put("subCode", subCode);
        result.put("subMsg", subMsg);
        return result;
    }

    private Map<String, Object> outcome(String outcome, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("outcome", outcome);
        result.put("msg", message);
        return result;
    }

    private Map<String, Object> unknown(String message) {
        Map<String, Object> result = outcome("UNKNOWN", message);
        result.put("success", false);
        return result;
    }

    private void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
