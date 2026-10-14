package org.hongtu.ai.application;

import org.hongtu.ai.client.AiBusinessDataClient;
import org.hongtu.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 校验 AI 店长经营范围。门店 ID 只接受当前账号已被授权的列表，不能由模型扩大权限。
 */
@Component
public class AiManagerScopeGuard {
    private final AiBusinessDataClient businessData;

    public AiManagerScopeGuard(AiBusinessDataClient businessData) {
        this.businessData = businessData;
    }

    /** 查询当前账号可选的租户或门店范围。 */
    public Map<String, Object> scope() {
        return businessData.scope();
    }

    /**
     * 拒绝未授权的租户总部或门店。
     *
     * @param scopeType TENANT 或 STORE
     * @param storeId   门店范围下的门店 ID
     */
    public void validate(String scopeType, Long storeId) {
        Map<String, Object> available = scope();
        if ("TENANT".equals(scopeType)) {
            if (!Boolean.TRUE.equals(available.get("tenantViewAvailable"))) {
                throw new BusinessException(403, "当前账号不能查看租户总部分析");
            }
            return;
        }
        if (!"STORE".equals(scopeType) || storeId == null) {
            throw new BusinessException(400, "请选择要分析的门店");
        }
        if (!storeAllowed(available.get("stores"), storeId)) {
            throw new BusinessException(403, "当前账号无权查看该门店分析");
        }
    }

    private boolean storeAllowed(Object values, Long storeId) {
        return values instanceof Collection<?> stores && stores.stream().anyMatch(item -> {
            if (!(item instanceof Map<?, ?> store)) return false;
            return Objects.equals(String.valueOf(store.get("storeId")), String.valueOf(storeId));
        });
    }
}
