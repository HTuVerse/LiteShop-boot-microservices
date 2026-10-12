package org.hongtu.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.util.PemUtil;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
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
        String key = cacheKey(tenantId, account);
        evictStale(alipayClients, tenantId, key);
        try {
            return alipayClients.computeIfAbsent(key, ignored -> new DefaultAlipayClient(
                    "https://openapi.alipay.com/gateway.do",
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
                value(account.getApiV2Key()), value(account.getAlipayPublicKey()));
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
                                RefundService refund, NotificationParser parser) {
    }
}
