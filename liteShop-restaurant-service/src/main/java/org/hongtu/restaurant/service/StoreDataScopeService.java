package org.hongtu.restaurant.service;

import org.hongtu.tenant.StoreContext;
import org.springframework.stereotype.Service;

import java.util.List;

/** 门店级数据权限。租户边界由 MyBatis-Plus tenant_id 拦截器负责。 */
@Service
public class StoreDataScopeService {

    public boolean requiresStoreRestriction() {
        return StoreContext.isRestricted();
    }

    public List<Long> getAllowedStoreIds() {
        return StoreContext.getStoreIds();
    }

    public void checkStoreAccess(Long storeId) {
        if (!requiresStoreRestriction()) {
            return;
        }
        if (storeId == null) {
            throw new SecurityException("门店账号不能操作租户级全局数据");
        }
        if (!getAllowedStoreIds().contains(storeId)) {
            throw new SecurityException("无权访问该门店");
        }
    }
}
