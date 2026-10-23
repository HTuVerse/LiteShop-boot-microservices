package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.DAO.SysRolePermissionMapper;
import org.hongtu.DTO.SysRole;
import org.hongtu.audit.AuditChanges;
import org.hongtu.audit.AuditContext;
import org.hongtu.audit.AuditOperation;
import org.hongtu.service.SysRolePermissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *
 *角色权限
 */
@RestController
@RequestMapping("/sys-role-permission")
public class SysRolePermissionController {
    @Resource
    SysRolePermissionService sysRolePermissionService;
    @Resource
    SysRoleMapper roleMapper;
    @Resource
    AuditChanges auditChanges;

    /**
     *列举角色菜单树
     *
     * */
    @GetMapping("/listMenuTreeByRole")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:role:assign"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result listMenuTreeByRole(Long roleId){
        if(roleId == null){
            return Result.error("角色ID不能为空");
        }
        return sysRolePermissionService.listMenuTreeByRole(roleId);
    }

    /**
     * 为角色分配权限
     *
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     * @return 操作结果
     */
    @PostMapping("/assign")
    @AuditOperation(module = "system:role", type = "PERMISSION_ASSIGN", objectType = "ROLE", summary = "调整角色权限", objectIdArgument = 0)
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:role:assign"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result assignPermissions(@RequestParam Long roleId, @RequestBody List<Long> permissionIds) {
        if (roleId == null) {
            return Result.error("角色ID不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        List<Long> before = roleMapper.selectPermissionIdsByRoleId(roleId);
        Result result = sysRolePermissionService.assignPermissions(roleId, permissionIds);
        if (Result.CODE_SUCCESS.equals(result.getCode())) {
            AuditContext.target(String.valueOf(roleId), role == null ? null : role.getTenantId());
            auditChanges.capture(java.util.Map.of("permissions", before),
                    java.util.Map.of("permissions", roleMapper.selectPermissionIdsByRoleId(roleId)), "permissions");
        }
        return result;
    }

}
