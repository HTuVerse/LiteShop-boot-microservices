package org.hongtu.api.constant;

/** 跨服务统一的 SaaS 权益错误码。 */
public final class SaasErrorCode {
    private SaasErrorCode() {}

    public static final int FEATURE_DISABLED = 4601;
    public static final int QUOTA_EXCEEDED = 4602;
    public static final int SUBSCRIPTION_INACTIVE = 4603;
    public static final int ENTITLEMENT_CONFIGURATION_INVALID = 4604;
    public static final int ENTITLEMENT_SERVICE_UNAVAILABLE = 4605;
}
