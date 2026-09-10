package org.hongtu.saas.constant;

import org.hongtu.api.constant.SaasFeatureCode;

import java.util.Set;

/** 已完成业务侧强制校验闭环、可以售卖的增值服务编码。 */
public final class SaasFeatureKeys {
    private SaasFeatureKeys() {}

    public static final String STORE_COUNT = SaasFeatureCode.STORE_COUNT;

    /**
     * 新增编码前必须先在对应业务服务完成：真实用量统计、并发占额、失败补偿和删除释放。
     */
    public static final Set<String> SELLABLE = Set.of(STORE_COUNT);

    public static boolean isSellable(String featureCode) {
        return featureCode != null && SELLABLE.contains(featureCode.trim().toLowerCase());
    }
}
