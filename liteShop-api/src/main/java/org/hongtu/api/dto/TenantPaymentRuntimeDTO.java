package org.hongtu.api.dto;

import lombok.Data;
import lombok.ToString;

/**
 * 支付服务使用的租户账户运行参数。只通过内部接口返回。
 */
@Data
public class TenantPaymentRuntimeDTO {
    private Long tenantId;
    /** 渠道：WECHAT 或 ALIPAY。 */
    private String channel;
    private Boolean enabled;
    private String appId;
    private String mchId;
    private String mchSerialNo;
    /** 当前渠道的支付回调地址。 */
    private String notifyUrl;
    /** 当前支付宝账户是否使用沙箱网关。 */
    private Boolean sandboxEnabled;
    @ToString.Exclude
    private String privateKey;
    @ToString.Exclude
    private String apiV3Key;
    /** 微信 APIv2 密钥，付款码（被扫）支付使用。 */
    @ToString.Exclude
    private String apiV2Key;
    @ToString.Exclude
    private String alipayPublicKey;
    /** 配置更新时间，用于刷新支付客户端缓存。 */
    private Long updatedAtEpochMilli;
}
