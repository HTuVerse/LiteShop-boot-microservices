package org.hongtu.saas.constant;

import org.hongtu.api.constant.SaasFeatureCode;

import java.util.Set;

/** 已完成业务侧强制校验闭环、可以售卖的增值服务编码。 */
public final class SaasFeatureKeys {
    private SaasFeatureKeys() {}

    public static final String STORE_COUNT = SaasFeatureCode.STORE_COUNT;

    /**
     * 新增编码前，数值权益需完成用量闭环；布尔权益需在业务接口完成服务端校验。
     */
    public static final String AI_MANAGER = SaasFeatureCode.AI_MANAGER;

    /** AI 生成次数由用户服务的原子计数器统计。 */
    public static final String AI_MANAGER_CALLS = SaasFeatureCode.AI_MANAGER_CALLS;

    public static final Set<String> SELLABLE = Set.of(STORE_COUNT, AI_MANAGER, AI_MANAGER_CALLS);

    public static boolean isSellable(String featureCode) {
        return featureCode != null && SELLABLE.contains(featureCode.trim().toLowerCase());
    }
}
