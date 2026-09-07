package org.hongtu.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Config.AlipayConfig;
import org.hongtu.params.AlipayTradePagePayRequestParams;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.service.AlipayService;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付服务实现类
 */
@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {

    @Resource
     AlipayClient alipayClient;
    
    @Resource
    AlipayConfig alipayConfig;

    @Override
    public String createPagePay(AlipayTradePagePayRequestParams pagePayRequest) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(pagePayRequest.getNotifyUrl());//填业务的回调地址
//        request.setReturnUrl(alipayConfig.getReturnUrl());

        // 构建业务请求参数（biz_content）- 严格基于官方文档参数
        Map<String, Object> bizContent = new HashMap<>();

        // 必填参数
        bizContent.put("out_trade_no", pagePayRequest.getOutTradeNo());
        bizContent.put("total_amount", pagePayRequest.getTotalAmount().toString());
        bizContent.put("subject", pagePayRequest.getSubject());
        bizContent.put("product_code", pagePayRequest.getProductCode());

        // 可选参数 - 只添加非空值（严格按照官方文档）
        addIfNotEmpty(bizContent, "qr_pay_mode", pagePayRequest.getQrPayMode());
        if (pagePayRequest.getQrcodeWidth() != null) {
            bizContent.put("qrcode_width", pagePayRequest.getQrcodeWidth());
        }
        addIfNotEmpty(bizContent, "goods_detail", pagePayRequest.getGoodsDetail());
        addIfNotEmpty(bizContent, "time_expire", pagePayRequest.getTimeExpire());
        addIfNotEmpty(bizContent, "sub_merchant", pagePayRequest.getSubMerchant());
        addIfNotEmpty(bizContent, "extend_params", pagePayRequest.getExtendParams());
        addIfNotEmpty(bizContent, "business_params", pagePayRequest.getBusinessParams());
        addIfNotEmpty(bizContent, "promo_params", pagePayRequest.getPromoParams());
        addIfNotEmpty(bizContent, "integration_type", pagePayRequest.getIntegrationType());
        addIfNotEmpty(bizContent, "request_from_url", pagePayRequest.getRequestFromUrl());
        addIfNotEmpty(bizContent, "store_id", pagePayRequest.getStoreId());
        addIfNotEmpty(bizContent, "merchant_order_no", pagePayRequest.getMerchantOrderNo());
        addIfNotEmpty(bizContent, "ext_user_info", pagePayRequest.getExtUserInfo());
        addIfNotEmpty(bizContent, "invoice_info", pagePayRequest.getInvoiceInfo());

        request.setBizContent(JSON.toJSONString(bizContent));

        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            if (response.isSuccess()) {
                log.info("支付宝电脑网站支付表单生成成功，订单号：{}", pagePayRequest.getOutTradeNo());
                return response.getBody();
            } else {
                log.error("创建支付宝电脑网站支付失败，订单号：{}，错误码：{}，错误信息：{}",
                        pagePayRequest.getOutTradeNo(), response.getSubCode(), response.getSubMsg());
                return null;
            }
        } catch (AlipayApiException e) {
            log.error("创建支付宝电脑网站支付异常，订单号：{}", pagePayRequest.getOutTradeNo(), e);
            return null;
        }
    }

    @Override
    public String createWapPay(AlipayTradeWapPayRequestParams wapPayRequest) {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(wapPayRequest.getNotifyUrl());
//        request.setReturnUrl(alipayConfig.getReturnUrl());
        // 构建业务请求参数（biz_content）- 严格基于官方文档参数
        Map<String, Object> bizContent = new HashMap<>();
        // 必填参数
        bizContent.put("out_trade_no", wapPayRequest.getOutTradeNo());
        bizContent.put("total_amount", wapPayRequest.getTotalAmount().toString());
        bizContent.put("subject", wapPayRequest.getSubject());
        bizContent.put("product_code", wapPayRequest.getProductCode());
        // 可选参数 - 只添加非空值（严格按照官方文档）
        addIfNotEmpty(bizContent, "auth_token", wapPayRequest.getAuthToken());
        addIfNotEmpty(bizContent, "quit_url", wapPayRequest.getQuitUrl());
        addIfNotEmpty(bizContent, "goods_detail", wapPayRequest.getGoodsDetail());
        addIfNotEmpty(bizContent, "time_expire", wapPayRequest.getTimeExpire());
        addIfNotEmpty(bizContent, "extend_params", wapPayRequest.getExtendParams());
        addIfNotEmpty(bizContent, "business_params", wapPayRequest.getBusinessParams());
        addIfNotEmpty(bizContent, "passback_params", wapPayRequest.getPassbackParams());
        addIfNotEmpty(bizContent, "merchant_order_no", wapPayRequest.getMerchantOrderNo());
        addIfNotEmpty(bizContent, "ext_user_info", wapPayRequest.getExtUserInfo());
        request.setBizContent(JSON.toJSONString(bizContent));

        try {
            AlipayTradeWapPayResponse response = alipayClient.pageExecute(request);
            if (response.isSuccess()) {
                log.info("支付宝手机网站支付表单生成成功，订单号：{}", wapPayRequest.getOutTradeNo());
                return response.getBody();
            } else {
                log.error("创建支付宝手机网站支付失败，订单号：{}，错误码：{}，错误信息：{}",
                        wapPayRequest.getOutTradeNo(), response.getSubCode(), response.getSubMsg());
                return null;
            }
        } catch (AlipayApiException e) {
            log.error("创建支付宝手机网站支付异常，订单号：{}", wapPayRequest.getOutTradeNo(), e);
            return null;
        }
    }

    @Override
    public Map<String, Object> queryTrade(String outTradeNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", outTradeNo);

        request.setBizContent(JSON.toJSONString(bizContent));

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.isSuccess());
            result.put("code", response.getCode());
            result.put("msg", response.getMsg());
            result.put("subCode", response.getSubCode());
            result.put("subMsg", response.getSubMsg());
            
            if (response.isSuccess()) {
                result.put("tradeNo", response.getTradeNo());
                result.put("outTradeNo", response.getOutTradeNo());
                result.put("buyerLogonId", response.getBuyerLogonId());
                result.put("tradeStatus", response.getTradeStatus());
                result.put("totalAmount", response.getTotalAmount());
                result.put("receiptAmount", response.getReceiptAmount());
                result.put("buyerPayAmount", response.getBuyerPayAmount());
                result.put("pointAmount", response.getPointAmount());
                result.put("invoiceAmount", response.getInvoiceAmount());
                result.put("sendPayDate", response.getSendPayDate());
                result.put("storeId", response.getStoreId());
                result.put("terminalId", response.getTerminalId());
                result.put("storeName", response.getStoreName());
                result.put("buyerUserId", response.getBuyerUserId());
            }
            
            return result;
        } catch (AlipayApiException e) {
            log.error("查询支付宝交易异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> refundTrade(String outTradeNo, String refundAmount, String refundReason) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("refund_amount", refundAmount);
        bizContent.put("refund_reason", refundReason);

        request.setBizContent(JSON.toJSONString(bizContent));

        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.isSuccess());
            result.put("code", response.getCode());
            result.put("msg", response.getMsg());
            result.put("subCode", response.getSubCode());
            result.put("subMsg", response.getSubMsg());
            
            if (response.isSuccess()) {
                result.put("tradeNo", response.getTradeNo());
                result.put("outTradeNo", response.getOutTradeNo());
                result.put("buyerLogonId", response.getBuyerLogonId());
                result.put("fundChange", response.getFundChange());
                result.put("refundFee", response.getRefundFee());
                result.put("gmtRefundPay", response.getGmtRefundPay());
                result.put("buyerUserId", response.getBuyerUserId());
            }
            
            return result;
        } catch (AlipayApiException e) {
            log.error("支付宝交易退款异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> closeTrade(String outTradeNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", outTradeNo);

        request.setBizContent(JSON.toJSONString(bizContent));

        try {
            AlipayTradeCloseResponse response = alipayClient.execute(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.isSuccess());
            result.put("code", response.getCode());
            result.put("msg", response.getMsg());
            result.put("subCode", response.getSubCode());
            result.put("subMsg", response.getSubMsg());
            
            if (response.isSuccess()) {
                result.put("tradeNo", response.getTradeNo());
                result.put("outTradeNo", response.getOutTradeNo());
            }
            
            return result;
        } catch (AlipayApiException e) {
            log.error("关闭支付宝交易异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    @Override
    public String notifyProcess(HttpServletRequest request) {
        Map<String, String> params = convertRequestParamsToMap(request);
        try {
            boolean signVerified = verifyNotify(params);

            if (signVerified) {
                // 验签成功
                String outTradeNo = params.get("out_trade_no");
                String tradeNo = params.get("trade_no");
                String tradeStatus = params.get("trade_status");
                String totalAmount = params.get("total_amount");
                String buyerId = params.get("buyer_id");
                String appId = params.get("app_id");
                
                // 支付宝交易状态
                // WAIT_BUYER_PAY：交易创建，等待买家付款
                // TRADE_CLOSED：未付款交易超时关闭，或支付完成后全额退款
                // TRADE_SUCCESS：交易支付成功
                // TRADE_FINISHED：交易结束，不可退款
                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    // 支付成功，处理业务逻辑
                    // 更新订单状态等操作...
                    log.info("支付宝异步通知：订单[{}]支付成功，交易号：{}", outTradeNo, tradeNo);
                }
                // 返回 success 告诉支付宝处理成功，不再重复通知
                return "success";
            } else {
                // 验签失败
                log.error("支付宝异步通知验签失败");
                return "failure";
            }
        } catch (Exception e) {
            log.error("支付宝异步通知处理异常", e);
            return "failure";
        }
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        try {
            boolean signatureValid = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
            boolean appIdValid = alipayConfig.getAppId() != null
                    && alipayConfig.getAppId().equals(params.get("app_id"));
            if (!signatureValid || !appIdValid) {
                log.warn("支付宝回调校验失败: signatureValid={}, appIdValid={}",
                        signatureValid, appIdValid);
            }
            return signatureValid && appIdValid;
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常", e);
            return false;
        }
    }

    @Override
    public String returnProcess(HttpServletRequest request) {
        Map<String, String> params = convertRequestParamsToMap(request);
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );

            if (signVerified) {
                // 验签成功
                String outTradeNo = params.get("out_trade_no");
                String tradeNo = params.get("trade_no");
                String totalAmount = params.get("total_amount");
                
                // 处理业务逻辑
                log.info("支付宝同步回调：订单[{}]支付成功，交易号：{}", outTradeNo, tradeNo);
                
                // 返回结果，可以是跳转到成功页面
                return "支付成功";
            } else {
                // 验签失败
                log.error("支付宝同步回调验签失败");
                return "验签失败";
            }
        } catch (AlipayApiException e) {
            log.error("支付宝同步回调处理异常", e);
            return "处理异常";
        }
    }

    /**
     * 将请求参数转换为Map
     */
    private Map<String, String> convertRequestParamsToMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }
        return params;
    }


    /**
     * 添加非空参数到Map
     */
    private void addIfNotEmpty(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }
} 
