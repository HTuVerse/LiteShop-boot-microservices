package org.hongtu.service.impl;

import org.hongtu.DTO.SysPermission;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.MenuScopeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.annotation.EnumValue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class PermissionMenuScopeTest {
    private final SysPermissionServiceImpl service = new SysPermissionServiceImpl();
    private SysPermission menu(String code, String component) {
        return new SysPermission().setPermissionName("测试菜单").setPermissionCode(code)
                .setPermissionType(PermissionTypeEnum.MENU).setRouterAddress("/test")
                .setComponentAddress(component);
    }
    private Object validate(SysPermission menu) {
        return ReflectionTestUtils.invokeMethod(service, "normalizeAndValidate", menu, false);
    }
    @Test void explicitSharedScopeSurvivesValidationAndMenuResponse() {
        var p = menu("system:level", "system/level/index").setMenuScope(MenuScopeEnum.ALL);
        assertNull(validate(p));
        assertEquals(MenuScopeEnum.ALL, service.convertToMenuRoute(p).getMenuScope());
    }
    @Test void legacyScopeDefaultsMatchFrontend() {
        var tenant = menu("system:level", "system/level/index");
        var platform = menu("platform:level-template", "system/level/index");
        var common = menu("common:console", "/src/views/dashboard/console/index.vue");
        assertNull(validate(tenant)); assertNull(validate(platform)); assertNull(validate(common));
        assertEquals(MenuScopeEnum.TENANT, tenant.getMenuScope());
        assertEquals(MenuScopeEnum.PLATFORM, platform.getMenuScope());
        assertEquals(MenuScopeEnum.ALL, common.getMenuScope());
    }
    @Test void directoryIsSharedAndActionInheritsItsPage() {
        var directory = menu("system:menu", "Layout").setMenuScope(MenuScopeEnum.TENANT);
        assertNull(validate(directory)); assertEquals(MenuScopeEnum.ALL, directory.getMenuScope());
        var action = menu("system:level:add", "").setParentId(1L)
                .setPermissionType(PermissionTypeEnum.BUTTON).setMenuScope(MenuScopeEnum.PLATFORM);
        assertNull(validate(action)); assertNull(action.getMenuScope());
    }
    @Test void enumSerializesNumericCodesAndAcceptsLegacyPayloads() throws Exception {
        var mapper = new ObjectMapper();
        for (var scope : MenuScopeEnum.values()) {
            assertEquals(scope.getCode().toString(), mapper.writeValueAsString(scope));
            assertEquals(scope, mapper.readValue(scope.getCode().toString(), MenuScopeEnum.class));
            assertEquals(scope, mapper.readValue(mapper.writeValueAsString(scope.name()), MenuScopeEnum.class));
        }
        assertNotNull(MenuScopeEnum.class.getDeclaredField("code").getAnnotation(EnumValue.class));
        assertEquals(MenuScopeEnum.ALL, mapper.readValue("{\"menuScope\":0}", SysPermission.class).getMenuScope());
    }
    @Test void invalidScopeIsRejectedRatherThanConvertedToDefault() {
        var mapper = new ObjectMapper();
        for (String value : new String[]{"3", "-1", "1.5", "true", "{}", "[]", "\"INVALID\"", "\"\""}) {
            assertThrows(Exception.class, () -> mapper.readValue(value, MenuScopeEnum.class));
        }
    }
}
