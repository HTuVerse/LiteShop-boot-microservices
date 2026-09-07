package org.hongtu.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import cn.dev33.satoken.exception.NotPermissionException;
import org.hongtu.controller.*;
import org.hongtu.security.OperationPermission;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationPermissionTest {
    private StpInterface previous;
    private StpLogic previousLogic;
    private List<String> codes = List.of();
    private List<String> roles = List.of("CUSTOM_MANAGER");

    @BeforeEach void setUp() {
        previous = SaManager.getStpInterface();
        previousLogic = StpUtil.getStpLogic();
        StpUtil.setStpLogic(new StpLogic("login") {
            @Override public Object getLoginId() { return 42L; }
        });
        SaManager.setStpInterface(new StpInterface() {
            public List<String> getPermissionList(Object id, String type) { return codes; }
            public List<String> getRoleList(Object id, String type) { return roles; }
        });
    }
    @AfterEach void tearDown() {
        SaManager.setStpInterface(previous);
        StpUtil.setStpLogic(previousLogic);
    }
    private void check(Class<?> controller, String name) {
        var method = Arrays.stream(controller.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        SaAnnotationStrategy.instance.checkMethodAnnotation.accept(method);
    }
    @Test void pagePermissionDoesNotGrantWriteOperations() {
        codes = List.of("system:role", "system:user");
        assertThrows(NotPermissionException.class, () -> check(SysRoleController.class, "addRole"));
        assertThrows(NotPermissionException.class, () -> check(SysRoleController.class, "deleteRole"));
        assertThrows(NotPermissionException.class, () -> check(SysRolePermissionController.class, "assignPermissions"));
        assertThrows(NotPermissionException.class, () -> check(SysUserController.class, "rechargeBalance"));
    }
    @Test void arbitraryRoleCanUseOnlyItsGrantedOperation() {
        codes = List.of("system:role:add");
        assertDoesNotThrow(() -> check(SysRoleController.class, "addRole"));
        assertThrows(NotPermissionException.class, () -> check(SysRoleController.class, "deleteRole"));
    }
    @Test void legacyAdminRoleDoesNotBypassOperationChecks() {
        roles = List.of("ADMIN");
        assertThrows(NotPermissionException.class, () -> check(SysUserController.class, "rechargeBalance"));
    }
    @Test void superAdminRetainsExplicitBypass() {
        roles = List.of("SUPER_ADMIN");
        assertDoesNotThrow(() -> check(SysRoleController.class, "deleteRole"));
        assertDoesNotThrow(() -> OperationPermission.order("gift"));
    }
    @Test void revocationTakesEffectOnNextCheck() {
        codes = List.of("system:user:reset-password");
        check(SysUserController.class, "adminResetPassword");
        codes = List.of();
        assertThrows(NotPermissionException.class, () -> check(SysUserController.class, "adminResetPassword"));
    }
    @Test void compositeSensitiveFieldRequiresItsOwnPermission() {
        codes = List.of("config:banner:edit");
        assertDoesNotThrow(() -> OperationPermission.requireChange("config:banner:status", 1, 1));
        assertDoesNotThrow(() -> OperationPermission.requireChange("config:banner:status", 1, null));
        assertThrows(NotPermissionException.class, () -> OperationPermission.requireChange("config:banner:status", 1, 0));
    }
    @Test void cashierSettlementDoesNotGrantGiftOrRefund() {
        codes = List.of("operations:direct-cashier:settle");
        assertDoesNotThrow(() -> OperationPermission.order("settle"));
        assertThrows(NotPermissionException.class, () -> OperationPermission.order("gift"));
        assertThrows(NotPermissionException.class, () -> OperationPermission.order("refund"));
    }
}
