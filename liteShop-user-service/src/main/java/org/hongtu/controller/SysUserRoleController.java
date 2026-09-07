package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
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
    /**
     * 为用户分配角色（会先清除原有角色，再重新分配）
     */
    @PostMapping("/assign")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:assign-role"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result assignRoles(@RequestParam Long userId, @RequestBody List<Long> roleIds) {
        return sysUserRoleService.assignRoles(userId, roleIds);
    }

/*    *//**
     * 移除用户的某个角色
     *//*
    @DeleteMapping("/remove")
    public Result removeRole(@RequestParam Long userId, @RequestParam Long roleId) {
        return sysUserRoleService.removeRole(userId, roleId);
    }*/
}
