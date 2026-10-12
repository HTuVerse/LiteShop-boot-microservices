package org.hongtu.config.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 租户支付账户。私钥、APIv3 密钥和支付宝公钥只保存密文。
 */
@Data
@TableName("tenant_payment_account")
public class TenantPaymentAccount {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 租户ID。 */
    private Long tenantId;
    /** 渠道：1微信，2支付宝。 */
    private Integer channel;
    /** 是否启用：0否，1是。 */
    private Integer enabled;
    /** 微信小程序 AppId 或支付宝应用 ID。 */
    private String appId;
    /** 微信商户号。 */
    private String mchId;
    /** 微信商户证书序列号。 */
    private String mchSerialNo;
    /** 加密后的微信商户或支付宝应用私钥。 */
    @ToString.Exclude
    private String privateKeyCiphertext;
    /** 加密后的微信 APIv3 密钥。 */
    @ToString.Exclude
    private String apiV3KeyCiphertext;
    /** 加密后的微信 APIv2 密钥（付款码支付用）。 */
    @ToString.Exclude
    private String apiV2KeyCiphertext;
    /** 加密后的支付宝公钥。 */
    @ToString.Exclude
    private String alipayPublicKeyCiphertext;
    /** 最后修改的租户管理员用户 ID。 */
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
