package org.hongtu.tenant;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantMetaObjectHandlerTest {

    private final TenantMetaObjectHandler handler = new TenantMetaObjectHandler();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldOverrideForgedTenantOnInsertAndUpdate() {
        TenantRecord record = new TenantRecord();
        record.setTenantId(9999L);
        TenantContext.setTenantId(1001L);

        handler.insertFill(SystemMetaObject.forObject(record));
        assertEquals(1001L, record.getTenantId());

        record.setTenantId(8888L);
        handler.updateFill(SystemMetaObject.forObject(record));
        assertEquals(1001L, record.getTenantId());
    }

    static class TenantRecord {
        private Long tenantId;

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }
    }
}
