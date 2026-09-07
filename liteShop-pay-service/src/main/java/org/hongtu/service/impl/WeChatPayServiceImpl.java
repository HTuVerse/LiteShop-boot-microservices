package org.hongtu.service.impl;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Config.WeChatPayConfig;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.params.WeChatPayTradeParam;
import org.hongtu.service.WeChatPayService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 微信支付服务实现类（使用微信官方SDK）
 * 参考官方文档：https://github.com/wechatpay-apiv3/wechatpay-java
 *
 * @author Ht
 * @since 2025-10-31
 */
@Slf4j
@Service
public class WeChatPayServiceImpl implements WeChatPayService {

    @Resource
    private WeChatPayConfig weChatPayConfig;

    private RSAAutoCertificateConfig config;
    private JsapiService jsapiService;
    private JsapiServiceExtension jsapiServiceExtension;
    private NativePayService nativePayService;
    private RefundService refundService;
    private NotificationParser notificationParser;

    @PostConstruct
    public void init() {
        try {
            // 检查配置是否完整
            if (!isConfigValid()) {
                log.warn("========================================");
                log.warn("⚠️  微信支付配置不完整，跳过初始化");
                log.warn("如需使用微信支付功能，请在 application.yml 中配置：");
                log.warn("  wechat.pay.mch-id: 您的商户号");
                log.warn("  wechat.pay.private-key-path: 证书文件路径");
                log.warn("  wechat.pay.mch-serial-no: 商户证书序列号");
                log.warn("  wechat.pay.api-v3-key: APIv3密钥");
                log.warn("========================================");
                return;
            }

            // 使用自动更新证书的配置（推荐）
            config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(weChatPayConfig.getMchId())
                    .privateKeyFromPath(weChatPayConfig.getPrivateKeyPath())
                    .merchantSerialNumber(weChatPayConfig.getMchSerialNo())
                    .apiV3Key(weChatPayConfig.getApiV3Key())
                    .build();

            // 初始化各种支付服务
            jsapiService = new JsapiService.Builder().config(config).build();
            // JsapiServiceExtension 提供了生成调起支付参数的方法
            jsapiServiceExtension = new JsapiServiceExtension.Builder().config(config).build();
            nativePayService = new NativePayService.Builder().config(config).build();
            refundService = new RefundService.Builder().config(config).build();

            // 初始化通知解析器（用于处理支付回调）
            notificationParser = new NotificationParser(config);

            log.info("✅ 微信支付配置初始化成功，商户号：{}", weChatPayConfig.getMchId());
        } catch (Exception e) {
            log.warn("========================================");
            log.warn("⚠️  微信支付配置初始化失败：{}", e.getMessage());
            log.warn("如果不使用微信支付功能，可以忽略此警告");
            log.warn("如需使用微信支付，请检查：");
            log.warn("  1. 配置参数是否正确");
            log.warn("  2. 证书文件路径是否存在");
            log.warn("  3. 证书文件格式是否正确");
            log.warn("========================================");
            // 不抛出异常，允许应用继续启动
        }
    }


    @Override
    public Map<String, Object> createJsapiPay(WeChatPayTradeParam param) {
        // 检查服务是否已初始化
        if (jsapiServiceExtension == null) {
            throw new RuntimeException("微信支付服务未初始化，请检查配置和证书文件");
        }

        try {
            com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request =
                    new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();

            request.setAppid(weChatPayConfig.getAppId());
            request.setMchid(weChatPayConfig.getMchId());
            request.setDescription(param.getDescription());
            request.setOutTradeNo(param.getOutTradeNo());
            request.setNotifyUrl(param.getNotifyUrl());

            // 设置订单金额（单位：分）
            com.wechat.pay.java.service.payments.jsapi.model.Amount amount =
                    new com.wechat.pay.java.service.payments.jsapi.model.Amount();
            amount.setTotal(param.getTotalAmount().multiply(new java.math.BigDecimal("100")).intValue());
            request.setAmount(amount);

            // 设置支付者openid
            com.wechat.pay.java.service.payments.jsapi.model.Payer payer = new com.wechat.pay.java.service.payments.jsapi.model.Payer();
            payer.setOpenid(param.getOpenid());
            request.setPayer(payer);

            // 设置附加信息
            if (param.getAttach() != null) {
                request.setAttach(param.getAttach());
            }

            // 设置订单失效时间（RFC 3339格式）
            if (param.getTimeExpire() != null && param.getTimeExpire() > 0) {
                request.setTimeExpire(
                        java.time.OffsetDateTime.now()
                                .plusMinutes(param.getTimeExpire())
                                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                );
            }

            // 使用 JsapiServiceExtension 一步完成下单并生成调起支付参数
            // 这个方法会：1. 调用prepay下单  2. 自动生成小程序调起支付的签名参数
            // 官方SDK会自动生成签名，无需手动处理
            PrepayWithRequestPaymentResponse paymentResponse =
                    jsapiServiceExtension.prepayWithRequestPayment(request);

            log.info("微信小程序支付下单成功，商户订单号: {}", param.getOutTradeNo());

            // 转换为Map返回给前端
            Map<String, Object> payParams = new HashMap<>();
            payParams.put("timeStamp", paymentResponse.getTimeStamp());
            payParams.put("nonceStr", paymentResponse.getNonceStr());
            payParams.put("package", paymentResponse.getPackageVal());
            payParams.put("signType", paymentResponse.getSignType());
            payParams.put("paySign", paymentResponse.getPaySign());

            return payParams;

        } catch (Exception e) {
            log.error("创建微信小程序支付失败", e);
            throw new RuntimeException("创建微信小程序支付失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String createNativePay(WeChatPayTradeParam param) {
        if (nativePayService == null) {
            throw new RuntimeException("微信支付服务未初始化，请检查配置和证书文件");
        }

        try {
            // 构建Native支付请求参数
            PrepayRequest request = new PrepayRequest();
            request.setAppid(weChatPayConfig.getAppId());
            request.setMchid(weChatPayConfig.getMchId());
            request.setDescription(param.getDescription());
            request.setOutTradeNo(param.getOutTradeNo());
            request.setNotifyUrl(weChatPayConfig.getNotifyUrl());

            // 设置订单金额（单位：分）
            com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                    new com.wechat.pay.java.service.payments.nativepay.model.Amount();
            amount.setTotal(param.getTotalAmount().multiply(new java.math.BigDecimal("100")).intValue());
            request.setAmount(amount);

            // 设置附加信息
            if (param.getAttach() != null) {
                request.setAttach(param.getAttach());
            }

            // 设置订单失效时间
            if (param.getTimeExpire() != null && param.getTimeExpire() > 0) {
                request.setTimeExpire(
                        java.time.OffsetDateTime.now()
                                .plusMinutes(param.getTimeExpire())
                                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                );
            }

            // 调用Native支付下单
            PrepayResponse response = nativePayService.prepay(request);
            log.info("微信Native支付下单成功，商户订单号: {}, 二维码链接: {}",
                    param.getOutTradeNo(), response.getCodeUrl());

            return response.getCodeUrl();

        } catch (Exception e) {
            log.error("创建微信Native支付失败", e);
            throw new RuntimeException("创建微信Native支付失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String createH5Pay(WeChatPayTradeParam param) {
        try {
            // H5支付需要使用H5PayService
            // 这里简化处理，实际使用时需要引入H5PayService
            log.warn("H5支付功能待实现");
            return null;
        } catch (Exception e) {
            log.error("创建微信H5支付失败", e);
            throw new RuntimeException("创建微信H5支付失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> queryTrade(String outTradeNo) {
        if (jsapiService == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", "微信支付服务未初始化");
            return result;
        }

        try {
            QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
            request.setMchid(weChatPayConfig.getMchId());
            request.setOutTradeNo(outTradeNo);

            Transaction transaction =
                    jsapiService.queryOrderByOutTradeNo(request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("transactionId", transaction.getTransactionId());
            result.put("outTradeNo", transaction.getOutTradeNo());
            result.put("tradeState", transaction.getTradeState());
            result.put("tradeStateDesc", transaction.getTradeStateDesc());
            result.put("successTime", transaction.getSuccessTime());

            if (transaction.getAmount() != null) {
                result.put("total", transaction.getAmount().getTotal());
                result.put("payerTotal", transaction.getAmount().getPayerTotal());
            }

            return result;

        } catch (Exception e) {
            log.error("查询微信支付订单失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> closeTrade(String outTradeNo) {
        if (jsapiService == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", "微信支付服务未初始化");
            return result;
        }

        try {
            CloseOrderRequest request = new CloseOrderRequest();
            request.setMchid(weChatPayConfig.getMchId());
            request.setOutTradeNo(outTradeNo);

            jsapiService.closeOrder(request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("msg", "订单关闭成功");
            return result;

        } catch (Exception e) {
            log.error("关闭微信支付订单失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> refund(String outTradeNo, String outRefundNo,
                                      String refundAmount, String totalAmount, String reason) {
        if (refundService == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", "微信支付服务未初始化");
            return result;
        }

        try {
            CreateRequest request = new CreateRequest();
            request.setOutTradeNo(outTradeNo);
            request.setOutRefundNo(outRefundNo);
            request.setReason(reason);
            request.setNotifyUrl(weChatPayConfig.getNotifyUrl() + "/refund");

            // 设置金额
            com.wechat.pay.java.service.refund.model.AmountReq amountReq =
                    new com.wechat.pay.java.service.refund.model.AmountReq();
            amountReq.setRefund(Long.parseLong(refundAmount));
            amountReq.setTotal(Long.parseLong(totalAmount));
            amountReq.setCurrency("CNY");
            request.setAmount(amountReq);

            Refund refund = refundService.create(request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("refundId", refund.getRefundId());
            result.put("outRefundNo", refund.getOutRefundNo());
            result.put("status", refund.getStatus());
            return result;

        } catch (Exception e) {
            log.error("微信支付退款失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> queryRefund(String outRefundNo) {
        if (refundService == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", "微信支付服务未初始化");
            return result;
        }

        try {
            QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
            request.setOutRefundNo(outRefundNo);

            Refund refund = refundService.queryByOutRefundNo(request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("refundId", refund.getRefundId());
            result.put("outRefundNo", refund.getOutRefundNo());
            result.put("status", refund.getStatus());
            result.put("successTime", refund.getSuccessTime());
            return result;

        } catch (Exception e) {
            log.error("查询微信支付退款失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    /**
     * 验证并解析微信支付回调通知（推荐使用）
     * 纯粹的验签和解密，不处理业务逻辑，返回解析后的数据
     */
    @Override
    public PaymentNotifyResult parseAndVerifyNotify(
            String requestBody, String signature, String timestamp, String nonce, String serial) {

        if (notificationParser == null) {
            throw new RuntimeException("微信支付通知解析器未初始化，请检查配置");
        }

        try {
            // 1. 构建回调请求参数
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(requestBody)
                    .build();

            // 2. 使用 NotificationParser 解析并验证签名
            // SDK 会自动验证签名，如果验证失败会抛出异常
            Transaction transaction = notificationParser.parse(requestParam, Transaction.class);

            log.info("微信支付回调验签成功：商户订单号[{}]，微信支付订单号：{}，交易状态：{}",
                    transaction.getOutTradeNo(),
                    transaction.getTransactionId(),
                    transaction.getTradeState());

            // 3. 封装解析结果
            PaymentNotifyResult result = PaymentNotifyResult.builder()
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

            log.info("微信支付回调解析完成：订单[{}]，是否成功：{}",
                    result.getOutTradeNo(), result.getIsSuccess());

            return result;

        } catch (Exception e) {
            log.error("验证并解析微信支付回调通知失败", e);
            throw new RuntimeException("验证签名或解析数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String handleRefundNotify(String requestBody, String signature, String timestamp,
                                     String nonce, String serial) {
        if (notificationParser == null) {
            log.error("微信支付通知解析器未初始化");
            return "{\"code\": \"FAIL\", \"message\": \"微信支付服务未初始化\"}";
        }

        try {
            // 构建请求参数
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(requestBody)
                    .build();

            // 解析退款通知
            Refund refund = notificationParser.parse(requestParam, Refund.class);

            log.info("微信退款回调通知：退款单[{}]退款成功，微信退款单号：{}",
                    refund.getOutRefundNo(), refund.getRefundId());

            // 处理退款成功的业务逻辑
            log.info("退款成功，待处理业务逻辑");

            return "{\"code\": \"SUCCESS\", \"message\": \"成功\"}";

        } catch (Exception e) {
            log.error("处理微信退款回调通知失败", e);
            return "{\"code\": \"FAIL\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 检查微信支付配置是否有效
     */
    private boolean isConfigValid() {
        if (weChatPayConfig == null) {
            return false;
        }

        // 检查商户号
        if (weChatPayConfig.getMchId() == null ||
                weChatPayConfig.getMchId().isEmpty() ||
                "1234567890".equals(weChatPayConfig.getMchId())) {
            return false;
        }

        // 检查私钥路径
        if (weChatPayConfig.getPrivateKeyPath() == null ||
                weChatPayConfig.getPrivateKeyPath().isEmpty() ||
                weChatPayConfig.getPrivateKeyPath().contains("/path/to/")) {
            return false;
        }

        // 检查证书序列号
        if (weChatPayConfig.getMchSerialNo() == null ||
                weChatPayConfig.getMchSerialNo().isEmpty() ||
                "1234567890ABCDEF1234567890ABCDEF12345678".equals(weChatPayConfig.getMchSerialNo())) {
            return false;
        }

        // 检查APIv3密钥
        if (weChatPayConfig.getApiV3Key() == null ||
                weChatPayConfig.getApiV3Key().isEmpty() ||
                "your-api-v3-key-32-characters".equals(weChatPayConfig.getApiV3Key())) {
            return false;
        }

        return true;
    }


}

