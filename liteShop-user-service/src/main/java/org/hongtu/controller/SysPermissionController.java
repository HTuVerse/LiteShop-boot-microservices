package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.hongtu.DTO.SysPermission;
import org.hongtu.audit.AuditChanges;
import org.hongtu.audit.AuditContext;
import org.hongtu.audit.AuditOperation;
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
    @Resource
    private AuditChanges auditChanges;

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
    @AuditOperation(module = "platform:permission", type = "CREATE", objectType = "PERMISSION", summary = "新增系统权限")
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

        Result result = sysPermissionService.addPermission(permission);
        if (Result.CODE_SUCCESS.equals(result.getCode()) && permission.getId() != null)
            AuditContext.target(String.valueOf(permission.getId()), null);
        return result;
    }

    /**
     * 更新权限
     */
    @PutMapping("/update")
    @AuditOperation(module = "platform:permission", type = "UPDATE", objectType = "PERMISSION", summary = "修改系统权限", objectIdArgument = 0)
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
        SysPermission before = sysPermissionService.getById(permission.getId());
        Result result = sysPermissionService.updatePermission(permission);
        if (Result.CODE_SUCCESS.equals(result.getCode()))
            auditChanges.capture(before, sysPermissionService.getById(permission.getId()),
                    "permissionName", "permissionCode", "permissionType", "parentId", "status");
        return result;
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/{id}")
    @AuditOperation(module = "platform:permission", type = "DELETE", objectType = "PERMISSION", summary = "删除系统权限", objectIdArgument = 0)
    @SaCheckRole("SUPER_ADMIN")
    public Result deletePermission(@PathVariable Long id) {
        SysPermission before = sysPermissionService.getById(id);
        Result result = sysPermissionService.deletePermission(id);
        if (Result.CODE_SUCCESS.equals(result.getCode()))
            auditChanges.capture(before, null, "permissionName", "permissionCode", "permissionType", "status");
        return result;
    }

    @PutMapping("/hierarchy")
    @AuditOperation(module = "platform:permission", type = "HIERARCHY", objectType = "PERMISSION_TREE", summary = "调整权限层级")
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
