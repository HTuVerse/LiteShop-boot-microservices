package org.hongtu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

/**
 * 租户支付账户管理端视图。密钥只表示是否已配置，不回显明文。
 */
@Data
public class TenantPaymentAccountView {
    /** 渠道：WECHAT 或 ALIPAY。 */
    private String channel;
    /** 是否启用。 */
    private Boolean enabled;
    /** 微信小程序 AppId 或支付宝应用 ID。 */
    private String appId;
    /** 微信商户号。 */
    private String mchId;
    /** 微信商户证书序列号。 */
    private String mchSerialNo;
    /** 是否已保存商户私钥。 */
    private boolean privateKeyConfigured;
    /** 是否已保存微信 APIv3 密钥。 */
    private boolean apiV3KeyConfigured;
    /** 是否已保存微信 APIv2 密钥（付款码支付用）。 */
    private boolean apiV2KeyConfigured;
    /** 是否已保存支付宝公钥。 */
    private boolean alipayPublicKeyConfigured;
    /** 新的商户私钥，留空表示不修改。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String privateKey;
    /** 新的微信 APIv3 密钥，留空表示不修改。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String apiV3Key;
    /** 新的微信 APIv2 密钥（付款码支付用），留空表示不修改。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String apiV2Key;
    /** 新的支付宝公钥，留空表示不修改。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String alipayPublicKey;
    /** 是否清除已保存的商户或应用私钥，仅停用渠道时允许。 */
    private Boolean clearPrivateKey;
    /** 是否清除已保存的微信 APIv3 密钥，仅停用渠道时允许。 */
    private Boolean clearApiV3Key;
    /** 是否清除已保存的微信 APIv2 密钥，仅停用渠道时允许。 */
    private Boolean clearApiV2Key;
    /** 是否清除已保存的支付宝公钥，仅停用渠道时允许。 */
    private Boolean clearAlipayPublicKey;
}
