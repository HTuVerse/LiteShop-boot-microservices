package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;

import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.DAO.SysRolePermissionMapper;
import org.hongtu.DTO.SysPermission;
import org.hongtu.DTO.SysRole;
import org.hongtu.DTO.SysRolePermission;
import org.hongtu.Result.Result;
import org.hongtu.VO.RoleMenuVO;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.service.SysPermissionService;
import org.hongtu.service.SysRolePermissionService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色权限关联表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Service
public class SysRolePermissionServiceImpl extends ServiceImpl<SysRolePermissionMapper, SysRolePermission> implements SysRolePermissionService {

    @Resource
    SysPermissionMapper sysPermissionMapper;
    @Resource
    SysRolePermissionMapper rolePermissionMapper;
    @Resource
    SysRoleMapper sysRoleMapper;
    @Resource
    SysPermissionService sysPermissionService;

    @Override
    public Result listMenuTreeByRole(Long roleId) {
        try {
            SysRole target = sysRoleMapper.selectById(roleId);
            if (target == null) return Result.error("角色不存在或不属于当前租户");
            // 1. 查询所有启用的菜单
           LambdaQueryWrapper<SysPermission> queryWrapper = new LambdaQueryWrapper<>();
           queryWrapper.eq(SysPermission::getStatus, StatusEnum.NORMAL)
               .orderByAsc(SysPermission::getParentId)
               .orderByAsc(SysPermission::getSort);;
           List<SysPermission> allMenus = sysPermissionMapper.selectList(queryWrapper);
            allMenus = assignableMenus(target, allMenus);
            // 2. 查询角色拥有的权限ID列表
            List<Long> rolePermissionIds = sysRoleMapper.selectPermissionIdsByRoleId(roleId);
            // 3. 构建树形结构并标记权限
            List<RoleMenuVO> menuTree = buildRoleMenuTreeWithAssigned(allMenus, rolePermissionIds, 0L);
            return Result.success(menuTree);
        } catch (Exception e) {
            return Result.error("菜单加载失败：" + e.getMessage());
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result assignPermissions(Long roleId, List<Long> permissionIds) {
        try {
            // 1. 检查角色是否存在
            SysRole role = sysRoleMapper.selectById(roleId);
            if (role == null) {
                return Result.error("角色不存在");
            }
            if (TenantContext.isSuperAdmin() && TenantContext.getTenantIdOrNull() == null
                    && role.getTenantId() != null) {
                return TenantScope.call(role.getTenantId(),
                        () -> assignPermissionsInCurrentTenant(roleId, permissionIds));
            }
            return assignPermissionsInCurrentTenant(roleId, permissionIds);
        } catch (Exception e) {
            throw new BusinessException(400, "权限分配失败：" + e.getMessage(), e);
        }
    }

    private Result assignPermissionsInCurrentTenant(Long roleId, List<Long> permissionIds) {
        try {
            SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getId, roleId).last("FOR UPDATE"));
            if (role == null) {
                return Result.error("角色不存在或不在当前租户");
            }
            if ("ADMIN".equals(role.getRoleCode()) && !StpUtil.hasRole("ADMIN") && !TenantContext.isSuperAdmin())
                throw new BusinessException(403, "仅租户管理员可配置管理员角色");
            List<Long> requested = permissionIds == null ? List.of() : permissionIds.stream().distinct().toList();
            List<SysPermission> available = assignableMenus(role, sysPermissionMapper.selectList(
                    new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getStatus, StatusEnum.NORMAL)));
            requested = org.hongtu.service.support.PermissionHierarchy.assignment(available, requested);
            java.util.Set<Long> allowed = available.stream().map(SysPermission::getId).collect(Collectors.toSet());
            if (requested.stream().anyMatch(id -> id == null || !allowed.contains(id))) {
                throw new BusinessException(403, "存在无效、已停用或超出可分配范围的权限");
            }
            if (!TenantContext.isSuperAdmin() && "SUPER_ADMIN".equals(role.getRoleCode())) {
                throw new BusinessException(403, "不能修改平台角色");
            }
            // 2. 查询该角色现有的权限ID列表
/*            LambdaQueryWrapper<SysRolePermission> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysRolePermission::getRoleId, roleId)
                    .select(SysRolePermission::getPermissionId);
            List<Long> existingPermissionIds = list(queryWrapper).stream()
                    .map(SysRolePermission::getPermissionId)
                    .collect(Collectors.toList());*/
            List<Long> existingPermissionIds = sysRoleMapper.selectPermissionIdsByRoleId(roleId);
            // 3. 如果新权限列表为空，则清空所有权限
            if (requested.isEmpty()) {
                if (!existingPermissionIds.isEmpty()) {
                    LambdaQueryWrapper<SysRolePermission> deleteWrapper = new LambdaQueryWrapper<>();
                    deleteWrapper.eq(SysRolePermission::getRoleId, roleId);
                    remove(deleteWrapper);
                }
                return Result.success("已清空角色权限");
            }
            // 4. 计算需要删除和添加的权限
            // 需要删除的权限ID：existingPermissionIds中有但permissionIds中没有的
            List<Long> normalizedRequested = requested;
            List<Long> toDeleteIds = existingPermissionIds.stream()
                    .filter(id -> !normalizedRequested.contains(id))
                    .collect(Collectors.toList());
            // 需要添加的权限ID：permissionIds中有但existingPermissionIds中没有的
            List<Long> toAddIds = requested.stream()
                    .filter(id -> !existingPermissionIds.contains(id))
                    .collect(Collectors.toList());
            // 5. 删除需要删除的权限
            if (!toDeleteIds.isEmpty()) {
                LambdaQueryWrapper<SysRolePermission> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(SysRolePermission::getRoleId, roleId)
                        .in(SysRolePermission::getPermissionId, toDeleteIds);
                remove(deleteWrapper);
            }
            // 6. 添加需要添加的权限
            if (!toAddIds.isEmpty()) {
                List<SysRolePermission> rolePermissions = new ArrayList<>();
                for (Long permissionId : toAddIds) {
                    SysRolePermission rolePermission = new SysRolePermission();
                    rolePermission.setTenantId(role.getTenantId());
                    rolePermission.setRoleId(roleId);
                    rolePermission.setPermissionId(permissionId);
                    rolePermissions.add(rolePermission);
                }
                if (!saveBatch(rolePermissions)) throw new IllegalStateException("保存角色权限失败");
            }
            return Result.success("权限分配成功");
        } catch (Exception e) {
            throw new BusinessException(400, "权限分配失败：" + e.getMessage(), e);
        }
    }

    private List<SysPermission> assignableMenus(SysRole role, List<SysPermission> menus) {
        if ("USER".equals(role.getRoleCode())) return List.of();
        java.util.Set<Long> owned = TenantContext.isSuperAdmin() ? null :
                sysPermissionMapper.selectMenuPermissionsByUserId(StpUtil.getLoginIdAsLong()).stream()
                        .map(SysPermission::getId).collect(Collectors.toSet());
        java.util.Set<Long> allowed = menus.stream()
                .filter(p -> role.getTenantId() == null || (p.getPermissionCode() != null && !p.getPermissionCode().startsWith("platform:")))
                .filter(p -> owned == null || owned.contains(p.getId()))
                .map(SysPermission::getId).collect(Collectors.toSet());
        // Ancestor directories only provide tree structure; unowned pages never become assignable.
        java.util.Map<Long, SysPermission> byId = menus.stream().collect(Collectors.toMap(SysPermission::getId, p -> p));
        for (Long id : new ArrayList<>(allowed)) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            SysPermission node = byId.get(id);
            while (node != null && node.getParentId() != null && seen.add(node.getId())) {
                node = byId.get(node.getParentId());
                if (node != null && List.of("Layout", "/index/index", "index/index", "index/index.vue").contains(node.getComponentAddress())
                        && (role.getTenantId() == null || !String.valueOf(node.getPermissionCode()).startsWith("platform:"))) allowed.add(node.getId());
            }
        }
        return menus.stream().filter(p -> allowed.contains(p.getId())).toList();
    }

    /**
     * 构建带有分配标记的角色菜单树
     * @param permissions 所有权限列表
     * @param assignedPermissionIds 已分配的权限ID列表
     * @param parentId 父权限ID
     * @return 菜单树
     */
    private List<RoleMenuVO> buildRoleMenuTreeWithAssigned(List<SysPermission> permissions,
                                                           List<Long> assignedPermissionIds,
                                                           Long parentId) {
        List<RoleMenuVO> result = new ArrayList<>();

        if (permissions == null || permissions.isEmpty()) {
            return result;
        }

        // 过滤出当前父ID下的权限
        List<SysPermission> currentLevelPermissions = permissions.stream()
                .filter(p -> (p.getParentId() == null && parentId == 0L) ||
                        (p.getParentId() != null && p.getParentId().equals(parentId)))
                .collect(Collectors.toList());

        // 构建当前层级的菜单
        for (SysPermission permission : currentLevelPermissions) {
            RoleMenuVO menuVO = convertToRoleMenu(permission);
            // 标记是否已分配
            menuVO.setIsAssigned(assignedPermissionIds.contains(permission.getId()));
            // 递归构建子菜单
            List<RoleMenuVO> children = buildRoleMenuTreeWithAssigned(
                    permissions, assignedPermissionIds, permission.getId());
            if (children != null && !children.isEmpty()) {
                menuVO.setChildren(children);
            }
            result.add(menuVO);
        }

        return result;
    }

    /**
     * 将权限对象转换为角色菜单对象
     * @param permission 权限对象
     * @return 角色菜单对象
     */
    private RoleMenuVO convertToRoleMenu(SysPermission permission) {
        RoleMenuVO menuVO = new RoleMenuVO();
        menuVO.setId(permission.getId());
        menuVO.setPermissionCode(permission.getPermissionCode());
        menuVO.setStatus(permission.getStatus());
        menuVO.setSort(permission.getSort());
        menuVO.setParentId(permission.getParentId());
        menuVO.setPermissionType(permission.getPermissionType());
        // 设置元数据
        menuVO.setMeta(permission.getPermissionName(), permission.getIcon(), true);

        return menuVO;
    }

}
