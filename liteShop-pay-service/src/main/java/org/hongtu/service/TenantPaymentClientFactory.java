package org.hongtu.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.util.PemUtil;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按租户支付账户创建并缓存微信、支付宝客户端。账户内容变化后立即刷新缓存。
 */
@Service
@Slf4j
public class TenantPaymentClientFactory {
    private final ConfigFeignClient configFeignClient;
    private final ConcurrentHashMap<String, WeChatClients> wechatClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AlipayClient> alipayClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WxPayService> wxMicroPayClients = new ConcurrentHashMap<>();
    private static final String ALIPAY_PRODUCTION_GATEWAY = "https://openapi.alipay.com/gateway.do";
    private static final String ALIPAY_SANDBOX_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    public TenantPaymentClientFactory(ConfigFeignClient configFeignClient) {
        this.configFeignClient = configFeignClient;
    }

    /** 加载当前命令指定租户的微信客户端。 */
    public WeChatClients wechat(Long tenantId) {
        TenantPaymentRuntimeDTO account = TenantPaymentAccountGuard.requireWeChat(load(tenantId, "WECHAT"));
        String key = cacheKey(tenantId, account);
        evictStale(wechatClients, tenantId, key);
        try {
            return wechatClients.computeIfAbsent(key, ignored -> buildWeChat(account));
        } catch (RuntimeException exception) {
            log.error("初始化租户微信支付客户端失败, tenantId={}", tenantId, exception);
            throw new BusinessException("当前租户微信支付账户初始化失败，请检查商户参数和网络");
        }
    }

    /** 加载当前命令指定租户的微信付款码客户端（WxJava，走 v2 接口）。 */
    public WxPayService wxMicroPay(Long tenantId) {
        TenantPaymentRuntimeDTO account = TenantPaymentAccountGuard.requireWeChatMicroPay(load(tenantId, "WECHAT"));
        String key = cacheKey(tenantId, account);
        evictStale(wxMicroPayClients, tenantId, key);
        try {
            return wxMicroPayClients.computeIfAbsent(key, ignored -> buildWxMicroPay(account));
        } catch (RuntimeException exception) {
            log.error("初始化租户微信付款码客户端失败, tenantId={}", tenantId, exception);
            throw new BusinessException("当前租户微信付款码账户初始化失败，请检查商户参数");
        }
    }

    /** 加载当前命令指定租户的支付宝客户端。 */
    public AlipayClient alipay(Long tenantId) {
        TenantPaymentRuntimeDTO account = TenantPaymentAccountGuard.requireAlipay(load(tenantId, "ALIPAY"));
        String gateway = alipayGateway(account.getSandboxEnabled());
        String key = cacheKey(tenantId, account);
        evictStale(alipayClients, tenantId, key);
        try {
            return alipayClients.computeIfAbsent(key, ignored -> new DefaultAlipayClient(
                    gateway,
                    account.getAppId(),
                    account.getPrivateKey(),
                    "JSON",
                    "utf-8",
                    account.getAlipayPublicKey(),
                    "RSA2"));
        } catch (RuntimeException exception) {
            log.error("初始化租户支付宝客户端失败, tenantId={}", tenantId, exception);
            throw new BusinessException("当前租户支付宝账户初始化失败，请检查应用参数");
        }
    }

    /** 读取支付宝公钥，供回调验签使用。 */
    public TenantPaymentRuntimeDTO alipayAccount(Long tenantId) {
        return TenantPaymentAccountGuard.requireAlipay(load(tenantId, "ALIPAY"));
    }

    /** 支付环境丢失时拒绝创建表单，避免沙箱应用误入正式网关。 */
    static String alipayGateway(Boolean sandboxEnabled) {
        if (sandboxEnabled == null) {
            throw new BusinessException("支付宝支付环境未同步，请检查配置服务与支付服务版本");
        }
        return sandboxEnabled ? ALIPAY_SANDBOX_GATEWAY : ALIPAY_PRODUCTION_GATEWAY;
    }

    /** 支付回调地址只由支付服务读取，调用方不传递渠道配置。 */
    public String notifyUrl(Long tenantId, String channel) {
        TenantPaymentRuntimeDTO account = load(tenantId, channel);
        if ("WECHAT".equals(channel)) {
            TenantPaymentAccountGuard.requireWeChat(account);
        } else if ("ALIPAY".equals(channel)) {
            TenantPaymentAccountGuard.requireAlipay(account);
        } else {
            throw new BusinessException("不支持的支付渠道");
        }
        return PaymentNotifyUrls.withTenant(account.getNotifyUrl(), tenantId, channel);
    }

    /** 微信退款通知沿用本租户的支付通知域名。 */
    public String wechatRefundNotifyUrl(Long tenantId) {
        return PaymentNotifyUrls.wechatRefund(notifyUrl(tenantId, "WECHAT"));
    }

    /** 其他业务仍使用其订单服务提供的通知地址。 */
    public String resolveWeChatNotifyUrl(Long tenantId, String callbackOwner, String suppliedUrl) {
        if ("RESTAURANT".equals(callbackOwner)) {
            return notifyUrl(tenantId, "WECHAT");
        }
        if (suppliedUrl == null || suppliedUrl.isBlank()) {
            throw new BusinessException("微信支付回调地址未配置");
        }
        return suppliedUrl;
    }

    private TenantPaymentRuntimeDTO load(Long tenantId, String channel) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("支付命令缺少租户");
        }
        return TenantScope.call(tenantId, () -> FeignResultUtil.unwrap(
                configFeignClient.paymentAccount(tenantId, channel)));
    }

    private WeChatClients buildWeChat(TenantPaymentRuntimeDTO account) {
        RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
                .merchantId(account.getMchId())
                .privateKey(PemUtil.loadPrivateKeyFromString(account.getPrivateKey()))
                .merchantSerialNumber(account.getMchSerialNo())
                .apiV3Key(account.getApiV3Key())
                .build();
        return new WeChatClients(
                account.getAppId(),
                account.getMchId(),
                new JsapiService.Builder().config(config).build(),
                new JsapiServiceExtension.Builder().config(config).build(),
                new H5Service.Builder().config(config).build(),
                new NativePayService.Builder().config(config).build(),
                new RefundService.Builder().config(config).build(),
                new NotificationParser(config));
    }

    /** 构建 WxJava 付款码客户端。付款码为 v2 接口，使用 APIv2 密钥（mchKey）。 */
    private WxPayService buildWxMicroPay(TenantPaymentRuntimeDTO account) {
        WxPayConfig config = new WxPayConfig();
        config.setAppId(account.getAppId());
        config.setMchId(account.getMchId());
        config.setMchKey(account.getApiV2Key());
        config.setUseSandboxEnv(false);
        WxPayService service = new WxPayServiceImpl();
        service.setConfig(config);
        return service;
    }

    private String cacheKey(Long tenantId, TenantPaymentRuntimeDTO account) {
        // 数据库时间精度或同一毫秒连续保存都不能导致旧客户端继续复用。
        String material = String.join("\u0000",
                value(account.getChannel()), value(account.getAppId()), value(account.getMchId()),
                value(account.getMchSerialNo()), value(account.getPrivateKey()), value(account.getApiV3Key()),
                value(account.getApiV2Key()), value(account.getAlipayPublicKey()),
                value(String.valueOf(account.getSandboxEnabled())));
        try {
            String fingerprint = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
            return tenantId + ":" + fingerprint;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private <T> void evictStale(ConcurrentHashMap<String, T> cache, Long tenantId, String currentKey) {
        String prefix = tenantId + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix) && !key.equals(currentKey));
    }

    /** 一个租户微信商户对应的官方 SDK 客户端。 */
    public record WeChatClients(String appId, String mchId, JsapiService query, JsapiServiceExtension pay,
                                H5Service h5, NativePayService nativePay, RefundService refund,
                                NotificationParser parser) {
    }
}
