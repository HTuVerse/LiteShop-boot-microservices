package org.hongtu.tenant;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 强制写入当前租户，防止客户端在新增/更新实体时伪造 tenantId。
 * <p>
 * 平台超级管理员处于全局模式（没有 TenantContext）时保留业务层显式指定的 tenantId，
 * 以支持平台创建租户用户、角色等受控操作。
 */
@Component
public class TenantMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        enforceCurrentTenant(metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        enforceCurrentTenant(metaObject);
    }

    private void enforceCurrentTenant(MetaObject metaObject) {
        Long tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null && metaObject.hasSetter("tenantId")) {
            metaObject.setValue("tenantId", tenantId);
        }
    }
}
