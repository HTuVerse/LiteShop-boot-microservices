package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.hongtu.DTO.SysPermission;
import org.hongtu.Result.Result;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.service.SysPermissionService;
import org.springframework.web.bind.annotation.*;

/**
 * 系统权限表 前端控制器
 *
 * @author Honghong
 * @since 2025-05-06
 */
@RestController
@RequestMapping("/sys-permission")
public class SysPermissionController {

    @Resource
    private SysPermissionService sysPermissionService;

    /**
     * 获取权限树
     */
    @GetMapping("/tree")
    @SaCheckRole("SUPER_ADMIN")
    public Result getPermissionTree() {
        return sysPermissionService.getPermissionTree();
    }

    /**
     * 添加权限
     */
    @PostMapping("/add")
    @SaCheckRole("SUPER_ADMIN")
    public Result addPermission(@RequestBody SysPermission permission) {
        if (permission == null) {
            return Result.error("权限信息不能为空");
        }
        // 检查必填字段
        if (StrUtil.isBlank(permission.getPermissionName())) {
            return Result.error("权限名称不能为空");
        }
        if (StrUtil.isBlank(permission.getPermissionCode())) {
            return Result.error("权限编码不能为空");
        }
        if (permission.getPermissionType() == null) {
            return Result.error("权限类型不能为空");
        }
        if (permission.getPermissionType() != PermissionTypeEnum.MENU
                && (permission.getParentId() == null || permission.getParentId() <= 0)) {
            return Result.error("按钮和API权限必须选择上级菜单");
        }
        if(permission.getPermissionType()== PermissionTypeEnum.MENU){
            if (StrUtil.isBlank(permission.getComponentAddress())) {
                return Result.error("组件地址不能为空");
            }
            if (StrUtil.isBlank(permission.getRouterAddress())) {
                return Result.error("路由地址不能为空");
            }
        }

        return sysPermissionService.addPermission(permission);
    }

    /**
     * 更新权限
     */
    @PutMapping("/update")
    @SaCheckRole("SUPER_ADMIN")
    public Result updatePermission(@RequestBody SysPermission permission) {
        // 检查必填字段
        if (permission == null || permission.getId() == null) {
            return Result.error("权限信息不完整");
        }
        if (StrUtil.isBlank(permission.getPermissionName())) {
            return Result.error("权限名称不能为空");
        }
        if (StrUtil.isBlank(permission.getPermissionCode())) {
            return Result.error("权限编码不能为空");
        }
        if (permission.getPermissionType() == null) {
            return Result.error("权限类型不能为空");
        }
        if (permission.getPermissionType() != PermissionTypeEnum.MENU
                && (permission.getParentId() == null || permission.getParentId() <= 0)) {
            return Result.error("按钮和API权限必须选择上级菜单");
        }
        return sysPermissionService.updatePermission(permission);
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/{id}")
    @SaCheckRole("SUPER_ADMIN")
    public Result deletePermission(@PathVariable Long id) {
        return sysPermissionService.deletePermission(id);
    }

    @PutMapping("/hierarchy")
    @SaCheckRole("SUPER_ADMIN")
    public Result updateHierarchy(@RequestBody org.hongtu.DTO.PermissionHierarchyRequest request) {
        return sysPermissionService.updateHierarchy(request);
    }


    /**
     * 获取当前用户的菜单路由
     */
    @GetMapping("/getCurrentUserMenus")
    public Result getCurrentUserMenus() {
        // 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        return sysPermissionService.getUserMenuRoutes(userId);
    }

}
