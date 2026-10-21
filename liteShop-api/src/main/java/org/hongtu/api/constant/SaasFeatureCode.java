package org.hongtu.api.constant;

/** 跨服务共享的 SaaS 增值服务编码。 */
public final class SaasFeatureCode {
    private SaasFeatureCode() {}

    /** 租户允许拥有的门店总数。 */
    public static final String STORE_COUNT = "store_count";

    /** 租户是否可使用 AI 店长。 */
    public static final String AI_MANAGER = "ai_manager";

    /** AI 店长每个订阅周期允许生成的次数。 */
    public static final String AI_MANAGER_CALLS = "ai_manager_calls";

    /** 租户资源库可占用的总字节数。 */
    public static final String RESOURCE_STORAGE_BYTES = "resource_storage_bytes";
}
