package org.hongtu.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hongtu.api.dto.TenantPaymentAccountView;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.config.DAO.TenantPaymentAccountMapper;
import org.hongtu.config.DTO.TenantPaymentAccount;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.StoreContext;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.List;

/**
 * 租户支付账户读写。门店会话不能查看或修改；运行参数只给支付服务。
 */
@Service
public class TenantPaymentAccountService {
    public static final String WECHAT = "WECHAT";
    public static final String ALIPAY = "ALIPAY";
    private static final int WECHAT_CODE = 1;
    private static final int ALIPAY_CODE = 2;

    private final TenantPaymentAccountMapper mapper;
    private final AiModelKeyCipher cipher;

    public TenantPaymentAccountService(TenantPaymentAccountMapper mapper, AiModelKeyCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    /** 返回微信和支付宝两个渠道的掩码配置，没有记录时也给出空表单。 */
    public List<TenantPaymentAccountView> listForEdit() {
        requireTenantOperator();
        return List.of(view(WECHAT, find(WECHAT_CODE)), view(ALIPAY, find(ALIPAY_CODE)));
    }

    /** 保存一个渠道。空密钥不覆盖已保存的密文。 */
    @Transactional(rollbackFor = Exception.class)
    public TenantPaymentAccountView save(TenantPaymentAccountView submitted, long userId) {
        requireTenantOperator();
        if (submitted == null) {
            throw new BusinessException("支付账户参数不能为空");
        }
        int channel = channelCode(submitted.getChannel());
        if (channel == WECHAT_CODE && Boolean.TRUE.equals(submitted.getSandboxEnabled())) {
            throw new BusinessException("微信支付 API v3 暂不支持小程序沙箱下单，不能启用微信沙箱环境");
        }
        TenantPaymentAccount current = find(channel);
        boolean creating = current == null;
        if (creating) {
            current = new TenantPaymentAccount();
            current.setTenantId(TenantContext.getTenantId());
            current.setChannel(channel);
            current.setPrivateKeyCiphertext("");
            current.setApiV3KeyCiphertext("");
            current.setApiV2KeyCiphertext("");
            current.setAlipayPublicKeyCiphertext("");
        }
        current.setEnabled(Boolean.TRUE.equals(submitted.getEnabled()) ? 1 : 0);
        current.setAppId(trimmed(submitted.getAppId(), 64, "应用标识"));
        current.setMchId(channel == WECHAT_CODE ? trimmed(submitted.getMchId(), 64, "微信商户号") : "");
        current.setMchSerialNo(channel == WECHAT_CODE
                ? trimmed(submitted.getMchSerialNo(), 128, "微信商户证书序列号") : "");
        current.setNotifyUrl(trimmed(submitted.getNotifyUrl(), 500, "支付回调地址"));
        current.setSandboxEnabled(Boolean.TRUE.equals(submitted.getSandboxEnabled()) ? 1 : 0);
        current.setPrivateKeyCiphertext(mergeSecret(current.getPrivateKeyCiphertext(),
                submitted.getPrivateKey(), submitted.getClearPrivateKey()));
        current.setApiV3KeyCiphertext(channel == WECHAT_CODE
                ? mergeSecret(current.getApiV3KeyCiphertext(), submitted.getApiV3Key(), submitted.getClearApiV3Key())
                : "");
        current.setApiV2KeyCiphertext(channel == WECHAT_CODE
                ? mergeSecret(current.getApiV2KeyCiphertext(), submitted.getApiV2Key(), submitted.getClearApiV2Key())
                : "");
        current.setAlipayPublicKeyCiphertext(channel == ALIPAY_CODE
                ? mergeSecret(current.getAlipayPublicKeyCiphertext(), submitted.getAlipayPublicKey(), submitted.getClearAlipayPublicKey())
                : "");
        if (current.getEnabled() == 1) {
            requireReady(channel, current);
        }
        current.setUpdatedBy(userId);
        current.setUpdatedAt(LocalDateTime.now());
        if (creating) {
            mapper.insert(current);
        } else if (mapper.updateById(current) != 1) {
            throw new BusinessException("支付账户保存失败");
        }
        return view(channel == WECHAT_CODE ? WECHAT : ALIPAY, current);
    }

    /**
     * 按指定租户读取已启用账户并解密。调用方租户与目标租户不一致时拒绝。
     */
    public TenantPaymentRuntimeDTO runtime(Long tenantId, String channel) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("支付账户缺少租户");
        }
        Long current = TenantContext.getTenantIdOrNull();
        if (current != null && !current.equals(tenantId)) {
            throw new BusinessException(403, "支付账户租户不匹配");
        }
        int channelCode = channelCode(channel);
        return TenantScope.call(tenantId, () -> {
            TenantPaymentAccount account = find(channelCode);
            if (account == null || account.getEnabled() == null || account.getEnabled() != 1) {
                return null;
            }
            TenantPaymentRuntimeDTO runtime = new TenantPaymentRuntimeDTO();
            runtime.setTenantId(tenantId);
            runtime.setChannel(channelCode == WECHAT_CODE ? WECHAT : ALIPAY);
            runtime.setEnabled(true);
            runtime.setAppId(account.getAppId());
            runtime.setMchId(account.getMchId());
            runtime.setMchSerialNo(account.getMchSerialNo());
            runtime.setNotifyUrl(account.getNotifyUrl());
            runtime.setSandboxEnabled(Integer.valueOf(1).equals(account.getSandboxEnabled()));
            runtime.setPrivateKey(cipher.decrypt(account.getPrivateKeyCiphertext()));
            runtime.setApiV3Key(channelCode == WECHAT_CODE ? cipher.decrypt(account.getApiV3KeyCiphertext()) : "");
            runtime.setApiV2Key(channelCode == WECHAT_CODE ? cipher.decrypt(account.getApiV2KeyCiphertext()) : "");
            runtime.setAlipayPublicKey(channelCode == ALIPAY_CODE ? cipher.decrypt(account.getAlipayPublicKeyCiphertext()) : "");
            runtime.setUpdatedAtEpochMilli(account.getUpdatedAt() == null ? 0L
                    : account.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            return runtime;
        });
    }

    /** 微信模拟支付开关；支付宝的同名字段只决定网关环境。 */
    public boolean sandboxEnabled(Long tenantId) {
        if (tenantId == null || tenantId <= 0) throw new BusinessException("支付账户缺少租户");
        Long current = TenantContext.getTenantIdOrNull();
        if (current != null && !current.equals(tenantId))
            throw new BusinessException(403, "支付账户租户不匹配");
        return TenantScope.call(tenantId, () -> sandboxEnabled(find(WECHAT_CODE)));
    }

    private boolean sandboxEnabled(TenantPaymentAccount account) {
        return account != null && Integer.valueOf(1).equals(account.getSandboxEnabled());
    }

    private void requireTenantOperator() {
        if (StoreContext.isRestricted()) {
            throw new BusinessException(403, "门店无权查看或修改支付账户");
        }
        if (!TenantContext.hasTenant()) {
            throw new BusinessException(403, "请选择租户后再配置支付账户");
        }
    }

    private void requireReady(int channel, TenantPaymentAccount account) {
        if (!StringUtils.hasText(account.getAppId()) || !StringUtils.hasText(account.getPrivateKeyCiphertext())) {
            throw new BusinessException("启用支付前请填写应用标识和商户私钥");
        }
        if (!StringUtils.hasText(account.getNotifyUrl())) {
            throw new BusinessException("启用支付前请填写回调地址");
        }
        requireNotifyUrl(channel, account.getNotifyUrl(), account.getTenantId());
        if (channel == WECHAT_CODE && (!StringUtils.hasText(account.getMchId())
                || !StringUtils.hasText(account.getMchSerialNo())
                || !StringUtils.hasText(account.getApiV3KeyCiphertext()))) {
            throw new BusinessException("启用微信支付前请填写商户号、证书序列号和 APIv3 密钥");
        }
        if (channel == WECHAT_CODE
                && cipher.decrypt(account.getApiV3KeyCiphertext()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length != 32) {
            throw new BusinessException("微信 APIv3 密钥必须为 32 字节");
        }
        // 微信 APIv2 密钥用于付款码支付，对启用 JSAPI 可选；一旦填写则必须为 32 字节。
        if (channel == WECHAT_CODE && StringUtils.hasText(account.getApiV2KeyCiphertext())
                && cipher.decrypt(account.getApiV2KeyCiphertext()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length != 32) {
            throw new BusinessException("微信 APIv2 密钥必须为 32 字节");
        }
        if (channel == ALIPAY_CODE && !StringUtils.hasText(account.getAlipayPublicKeyCiphertext())) {
            throw new BusinessException("启用支付宝前请填写支付宝公钥");
        }
    }

    /** 保存时校验渠道回调地址，避免启用后才在发起支付时失败。 */
    private void requireNotifyUrl(int channel, String notifyUrl, Long tenantId) {
        String expectedPath = channel == WECHAT_CODE ? "/wechatPay/notify" : "/alipayPay/notify";
        try {
            URI uri = URI.create(notifyUrl);
            String path = uri.getPath();
            if ("https".equals(uri.getScheme()) && StringUtils.hasText(uri.getHost())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && !notifyUrl.contains("your-domain")
                    && (expectedPath.equals(path) || (expectedPath + "/" + tenantId).equals(path))) return;
        } catch (IllegalArgumentException ignored) {
            // 无效 URI 统一返回可操作的配置错误。
        }
        throw new BusinessException("支付回调地址须为 HTTPS 公网地址，路径以 " + expectedPath + " 结尾");
    }

    private String mergeSecret(String currentCiphertext, String submitted, Boolean clear) {
        String value = submitted == null ? "" : submitted.trim();
        if (Boolean.TRUE.equals(clear) && StringUtils.hasText(value)) {
            throw new BusinessException("清除密钥和替换密钥不能同时执行");
        }
        if (Boolean.TRUE.equals(clear)) {
            return "";
        }
        if (!StringUtils.hasText(value)) {
            return currentCiphertext == null ? "" : currentCiphertext;
        }
        if (value.length() > 32768) {
            throw new BusinessException("支付密钥内容过长");
        }
        return cipher.encrypt(value);
    }

    private TenantPaymentAccount find(int channel) {
        return mapper.selectOne(new LambdaQueryWrapper<TenantPaymentAccount>()
                .eq(TenantPaymentAccount::getChannel, channel)
                .last("LIMIT 1"));
    }

    private TenantPaymentAccountView view(String channel, TenantPaymentAccount account) {
        TenantPaymentAccountView view = new TenantPaymentAccountView();
        view.setChannel(channel);
        view.setEnabled(account != null && Integer.valueOf(1).equals(account.getEnabled()));
        view.setAppId(account == null ? "" : account.getAppId());
        view.setMchId(account == null ? "" : account.getMchId());
        view.setMchSerialNo(account == null ? "" : account.getMchSerialNo());
        view.setNotifyUrl(account == null ? "" : account.getNotifyUrl());
        view.setSandboxEnabled(account != null && Integer.valueOf(1).equals(account.getSandboxEnabled()));
        view.setPrivateKeyConfigured(account != null && StringUtils.hasText(account.getPrivateKeyCiphertext()));
        view.setApiV3KeyConfigured(account != null && StringUtils.hasText(account.getApiV3KeyCiphertext()));
        view.setApiV2KeyConfigured(account != null && StringUtils.hasText(account.getApiV2KeyCiphertext()));
        view.setAlipayPublicKeyConfigured(account != null && StringUtils.hasText(account.getAlipayPublicKeyCiphertext()));
        return view;
    }

    private int channelCode(String channel) {
        if (WECHAT.equalsIgnoreCase(channel)) {
            return WECHAT_CODE;
        }
        if (ALIPAY.equalsIgnoreCase(channel)) {
            return ALIPAY_CODE;
        }
        throw new BusinessException("不支持的支付渠道");
    }

    private String trimmed(String value, int maxLength, String fieldName) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maxLength) {
            throw new BusinessException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return result;
    }
}
