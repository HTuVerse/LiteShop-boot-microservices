package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.DAO.SysUserRoleMapper;
import org.hongtu.audit.AuditChanges;
import org.hongtu.audit.AuditContext;
import org.hongtu.audit.AuditOperation;
import org.hongtu.service.SysUserRoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户角色关联表 前端控制器
 *
 * @author Honghong
 * @since 2025-05-06
 */
@RestController
@RequestMapping("/sys-user-role")
public class SysUserRoleController {

    @Resource
    private SysUserRoleService sysUserRoleService;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private AuditChanges auditChanges;
    /**
     * 为用户分配角色（会先清除原有角色，再重新分配）
     */
    @PostMapping("/assign")
    @AuditOperation(module = "system:user", type = "ROLE_ASSIGN", objectType = "USER", summary = "调整用户角色", objectIdArgument = 0)
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:assign-role"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result assignRoles(@RequestParam Long userId, @RequestBody List<Long> roleIds) {
        List<String> before = userRoleMapper.getRoleCodesByUserId(userId);
        Result result = sysUserRoleService.assignRoles(userId, roleIds);
        if (Result.CODE_SUCCESS.equals(result.getCode())) {
            AuditContext.target(String.valueOf(userId), org.hongtu.tenant.TenantContext.getTenantIdOrNull());
            auditChanges.capture(java.util.Map.of("roles", before),
                    java.util.Map.of("roles", userRoleMapper.getRoleCodesByUserId(userId)), "roles");
        }
        return result;
    }

/*    *//**
     * 移除用户的某个角色
     *//*
    @DeleteMapping("/remove")
    public Result removeRole(@RequestParam Long userId, @RequestParam Long roleId) {
        return sysUserRoleService.removeRole(userId, roleId);
    }*/
}
