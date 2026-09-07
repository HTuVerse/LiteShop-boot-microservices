package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.hongtu.DTO.SysRole;
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
        return sysRoleService.addRole(role);
    }

    /**
     * 更新角色
     */
    @PutMapping("/update")
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

        return sysRoleService.updateRole(role);
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:role:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteRole(@PathVariable Long id) {
        return sysRoleService.deleteRole(id);
    }
}
