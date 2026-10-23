package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.hongtu.DTO.SysRole;
import org.hongtu.audit.AuditChanges;
import org.hongtu.audit.AuditContext;
import org.hongtu.audit.AuditOperation;
import org.hongtu.Result.Result;
import org.hongtu.service.SysRoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统角色表 前端控制器
 *
 * @author Honghong
 * @since 2025-05-06
 */
@RestController
@RequestMapping("/sys-role")
public class SysRoleController {

    @Resource
    SysRoleService sysRoleService;
    @Resource
    AuditChanges auditChanges;

    /**
     * 获取所有角色列表
     */
    @GetMapping("/list")
    @SaCheckPermission(value = {"system:role", "system:user"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<SysRole>> getAllRoles() {
        return sysRoleService.getAllRoles();
    }

    /**
     * 添加角色
     */
    @PostMapping("/add")
    @AuditOperation(module = "system:role", type = "CREATE", objectType = "ROLE", summary = "新增角色")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:role:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result addRole(@RequestBody SysRole role) {
        if (role == null) {
            return Result.error("角色信息不能为空");
        }
        // 检查必填字段
        if (StrUtil.isBlank(role.getRoleName())) {
            return Result.error("角色名称不能为空");
        }
        if (StrUtil.isBlank(role.getRoleCode())) {
            return Result.error("角色编码不能为空");
        }
        Result result = sysRoleService.addRole(role);
        if (Result.CODE_SUCCESS.equals(result.getCode())) AuditContext.target(String.valueOf(role.getId()), role.getTenantId());
        return result;
    }

    /**
     * 更新角色
     */
    @PutMapping("/update")
    @AuditOperation(module = "system:role", type = "UPDATE", objectType = "ROLE", summary = "修改角色", objectIdArgument = 0)
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:role:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result updateRole(@RequestBody SysRole role) {
        if (role == null || role.getId() == null) {
            return Result.error("角色信息不完整");
        }
        if (StrUtil.isBlank(role.getRoleName())) {
            return Result.error("角色名称不能为空");
        }
        if (StrUtil.isBlank(role.getRoleCode())) {
            return Result.error("角色编码不能为空");
        }

        SysRole before = sysRoleService.getById(role.getId());
        Result result = sysRoleService.updateRole(role);
        if (Result.CODE_SUCCESS.equals(result.getCode())) {
            SysRole after = sysRoleService.getById(role.getId());
            AuditContext.target(String.valueOf(role.getId()), after == null ? null : after.getTenantId());
            auditChanges.capture(before, after, "roleName", "roleCode", "description", "status");
        }
        return result;
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @AuditOperation(module = "system:role", type = "DELETE", objectType = "ROLE", summary = "删除角色", objectIdArgument = 0)
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:role:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteRole(@PathVariable Long id) {
        SysRole before = sysRoleService.getById(id);
        Result result = sysRoleService.deleteRole(id);
        if (Result.CODE_SUCCESS.equals(result.getCode())) {
            AuditContext.target(String.valueOf(id), before == null ? null : before.getTenantId());
            auditChanges.capture(before, null, "roleName", "roleCode", "description", "status");
        }
        return result;
    }
}
