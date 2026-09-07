package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
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
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:role:assign"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result assignPermissions(@RequestParam Long roleId, @RequestBody List<Long> permissionIds) {
        if (roleId == null) {
            return Result.error("角色ID不能为空");
        }
        return sysRolePermissionService.assignPermissions(roleId, permissionIds);
    }

}
