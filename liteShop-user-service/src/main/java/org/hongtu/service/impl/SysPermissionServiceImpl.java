package org.hongtu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import jakarta.annotation.Resource;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.DAO.SysRolePermissionMapper;
import org.hongtu.DTO.SysPermission;
import org.hongtu.Result.Result;
import org.hongtu.VO.MenuRouteVO;
import org.hongtu.VO.SysPermissionVO;
import org.hongtu.service.SysPermissionService;
import org.hongtu.service.support.PermissionHierarchy;
import org.hongtu.DTO.PermissionHierarchyRequest;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.MenuScopeEnum;
import org.hongtu.enums.StatusEnum;
import cn.hutool.core.util.StrUtil;
import org.hongtu.utils.BeanCopyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 系统权限表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {

    @Resource
    SysRolePermissionMapper sysRolePermissionMapper;

    @Override
    public Result getPermissionTree() {
        // 查询所有权限
        List<SysPermission> menuPermissions = baseMapper.selectAllPermissions();
        if (menuPermissions == null || menuPermissions.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 构建菜单路由树
        List<MenuRouteVO> menuRoutes = buildMenuRouteTree(menuPermissions, 0L);
        return Result.success(menuRoutes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addPermission(SysPermission permission) {
        Result validation = normalizeAndValidate(permission, false);
        if (validation != null) {
            return validation;
        }
        // 检查父权限是否存在
        if (permission.getParentId() != null && permission.getParentId() > 0) {
            SysPermission parentPermission = getById(permission.getParentId());
            if (parentPermission == null) {
                return Result.error("父权限不存在");
            }
            if (parentPermission.getPermissionType() != PermissionTypeEnum.MENU) {
                return Result.error("上级权限必须是菜单");
            }
            if (permission.getPermissionType() == PermissionTypeEnum.MENU && !PermissionHierarchy.isDirectory(parentPermission))
                return Result.error("菜单只能放入目录，页面下只能添加按钮或API");
        } else {
            // 默认顶级权限
            permission.setParentId(0L);
        }

        // 检查权限编码是否已存在
        Integer count = baseMapper.checkPermissionCodeExists(permission.getPermissionCode());
        if (count != null && count > 0) {
            return Result.error("权限编码已存在");
        }


        // 保存权限
        boolean success = save(permission);
        if (success) {
            return Result.success("添加权限成功", permission);
        } else {
            return Result.error("添加权限失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updatePermission(SysPermission permission) {
        // 检查权限是否存在
        SysPermission existingPermission = getById(permission.getId());
        if (existingPermission == null) {
            return Result.error("权限不存在");
        }
        // Older clients omit this field: preserve the saved scope.
        if (permission.getMenuScope() == null) permission.setMenuScope(existingPermission.getMenuScope());
        Result validation = normalizeAndValidate(permission, true);
        if (validation != null) {
            return validation;
        }

        // 检查父权限是否存在
        if (permission.getParentId() != null && permission.getParentId() > 0) {
            // 不能将自己设为自己的父权限
            if (permission.getParentId().equals(permission.getId())) {
                return Result.error("不能将自己设为自己的父权限");
            }

            SysPermission parentPermission = getById(permission.getParentId());
            if (parentPermission == null) {
                return Result.error("父权限不存在");
            }
            if (parentPermission.getPermissionType() != PermissionTypeEnum.MENU) {
                return Result.error("上级权限必须是菜单");
            }
            if (permission.getPermissionType() == PermissionTypeEnum.MENU && !PermissionHierarchy.isDirectory(parentPermission))
                return Result.error("菜单只能放入目录，页面下只能添加按钮或API");

            // 检查是否形成循环引用
            Long tempParentId = parentPermission.getParentId();
            java.util.Set<Long> visited = new java.util.HashSet<>();
            while (tempParentId != null && tempParentId > 0) {
                if (!visited.add(tempParentId) || tempParentId.equals(permission.getId())) {
                    return Result.error("不能形成循环引用");
                }
                SysPermission tempParent = getById(tempParentId);
                if (tempParent == null) {
                    break;
                }
                tempParentId = tempParent.getParentId();
            }
        } else {
            // 默认顶级权限
            permission.setParentId(0L);
        }

        // 如果修改了权限编码，检查是否与其他权限冲突
        if (!existingPermission.getPermissionCode().equals(permission.getPermissionCode())) {
            Integer count = baseMapper.checkPermissionCodeExists(permission.getPermissionCode());
            if (count != null && count > 0) {
                return Result.error("权限编码已被使用");
            }
        }
        Integer childCount = baseMapper.countChildrenByParentId(permission.getId());
        if (!PermissionHierarchy.isDirectory(permission) && baseMapper.selectPermissionsByParentId(permission.getId())
                .stream().anyMatch(child -> child.getPermissionType() == PermissionTypeEnum.MENU))
            return Result.error("目录下存在菜单，不能将目录改成页面或按钮");
        if (permission.getPermissionType() != PermissionTypeEnum.MENU
                && childCount != null && childCount > 0) {
            return Result.error("存在下级权限时不能改为按钮或API");
        }
        LocalDateTime now = LocalDateTime.now();
        permission.setUpdateTime(now);
        boolean success = update(new LambdaUpdateWrapper<SysPermission>()
                .eq(SysPermission::getId, permission.getId())
                .set(SysPermission::getPermissionName, permission.getPermissionName())
                .set(SysPermission::getPermissionCode, permission.getPermissionCode())
                .set(SysPermission::getMenuScope, permission.getMenuScope())
                .set(SysPermission::getParentId, permission.getParentId())
                .set(SysPermission::getPermissionType, permission.getPermissionType())
                .set(SysPermission::getComponentAddress, permission.getComponentAddress())
                .set(SysPermission::getRouterAddress, permission.getRouterAddress())
                .set(SysPermission::getSort, permission.getSort())
                .set(SysPermission::getStatus, permission.getStatus())
                .set(SysPermission::getIcon, permission.getIcon())
                .set(SysPermission::getLink, permission.getLink())
                .set(SysPermission::getIsHide, permission.getIsHide())
                .set(SysPermission::getIsIframe, permission.getIsIframe())
                .set(SysPermission::getKeepAlive, permission.getKeepAlive())
                .set(SysPermission::getUpdateTime, now));
        if (success) {
            return Result.success("更新权限成功");
        } else {
            return Result.error("更新权限失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deletePermission(Long id) {
        if (id == null) {
            return Result.error("权限ID不能为空");
        }

        // 检查权限是否存在
        SysPermission permission = getById(id);
        if (permission == null) {
            return Result.error("权限不存在");
        }

        // 检查是否有子权限
        Integer childCount = baseMapper.countChildrenByParentId(id);
        if (childCount != null && childCount > 0) {
            return Result.error("该权限下有子权限，请先删除子权限");
        }
        // 全局权限删除时同步清理所有租户及平台角色的关联。
        TenantIgnore.run(() -> sysRolePermissionMapper.deleteByPermissionId(id));
        boolean success = removeById(id);
        if (success) {
            return Result.success("删除权限成功");
        } else {
            return Result.error("删除权限失败");
        }
    }


    @Override
    public Result getUserMenuRoutes(Long userId) {
        if (userId == null) {
            return Result.error("用户ID不能为空");
        }
        // 查询用户的菜单权限
        List<SysPermission> menuPermissions = TenantContext.isSuperAdmin()
                ? TenantIgnore.get(() -> baseMapper.selectMenuPermissionsByUserId(userId))
                : baseMapper.selectMenuPermissionsByUserId(userId);
        if (menuPermissions == null || menuPermissions.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 构建菜单路由树
        List<MenuRouteVO> menuRoutes = buildMenuRouteTree(
                PermissionHierarchy.navigation(menuPermissions, baseMapper.selectAllPermissions()), 0L);

        return Result.success(menuRoutes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateHierarchy(PermissionHierarchyRequest request) {
        List<PermissionHierarchy.Change> changes;
        try {
            changes = PermissionHierarchy.plan(baseMapper.selectHierarchyForUpdate(), request);
        } catch (IllegalArgumentException exception) {
            return Result.error(exception.getMessage());
        }
        for (var change : changes) {
            if (baseMapper.updateHierarchy(change.id(), change.parentId(), change.sort(), change.routerAddress()) != 1)
                throw new IllegalStateException("菜单保存失败，全部调整已回滚，请刷新后重试");
        }
        return Result.success("菜单层级与排序已保存");
    }

    @Override
    public List<SysPermissionVO> buildPermissionTree(List<SysPermission> permissions, Long parentId) {
        List<SysPermissionVO> result = new ArrayList<>();

        if (permissions == null || permissions.isEmpty()) {
            return result;
        }

        // 转换为VO对象
        List<SysPermissionVO> permissionVOList = permissions.stream()
                .map(permission -> BeanCopyUtils.copyBean(permission, SysPermissionVO.class))
                .collect(Collectors.toList());

        // 按父ID分组
        Map<Long, List<SysPermissionVO>> permissionMap = permissionVOList.stream()
                .collect(Collectors.groupingBy(SysPermissionVO::getParentId));

        // 获取指定父ID下的权限
        List<SysPermissionVO> parentPermissions = permissionMap.getOrDefault(parentId, new ArrayList<>());

        // 递归构建子节点
        for (SysPermissionVO permissionVO : parentPermissions) {
            permissionVO.setChildren(buildPermissionTree(permissions, permissionVO.getId()));
            result.add(permissionVO);
        }

        return result;
    }

    /**
     * 构建菜单路由树
     * @param permissions 权限列表
     * @param parentId 父权限ID
     * @return 菜单路由树
     */
    @Override
    public List<MenuRouteVO> buildMenuRouteTree(List<SysPermission> permissions, Long parentId) {
        List<MenuRouteVO> result = new ArrayList<>();

        if (permissions == null || permissions.isEmpty()) {
            return result;
        }

        // 过滤出当前父ID下的权限
        List<SysPermission> currentLevelPermissions = permissions.stream()
                .filter(p -> (p.getParentId() == null && parentId == 0L) ||
                        (p.getParentId() != null && p.getParentId().equals(parentId)))
                .collect(Collectors.toList());


        // 构建当前层级的菜单路由
        for (SysPermission permission : currentLevelPermissions) {

            MenuRouteVO menuRoute = convertToMenuRoute(permission);

            // 递归构建子菜单
            List<MenuRouteVO> children = buildMenuRouteTree(permissions, permission.getId());
            if (!children.isEmpty()) {
                menuRoute.setChildren(children);
            }

            result.add(menuRoute);
        }

        return result;
    }

    /**
     * 将权限对象转换为菜单路由对象
     * @param permission 权限对象
     * @return 菜单路由对象
     */
    public MenuRouteVO convertToMenuRoute(SysPermission permission) {
        MenuRouteVO menuRoute = new MenuRouteVO();
        menuRoute.setId(permission.getId());

        // 处理路径
        menuRoute.setPath(permission.getRouterAddress());

        // 处理名称
        String name = permission.getRouterAddress();
        if (name != null && name.contains("/")) {
            // 如果名称包含'/'，取最后一段
            String[] parts = name.split("/");
            name = parts[parts.length - 1];
        }
        /*   // 首字母大写
        if (name != null && !name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }*/
        menuRoute.setName(name);

        // 设置组件
        menuRoute.setComponent(permission.getComponentAddress());

        // 设置元数据
        Map<String, Object> meta = new HashMap<>();
        meta.put("title", permission.getPermissionName());
        meta.put("icon", permission.getIcon());
        meta.put("keepAlive", permission.getKeepAlive());
        meta.put("isHide",permission.getIsHide());
        meta.put("link",permission.getLink());
        meta.put("isIframe",permission.getIsIframe());
        menuRoute.setMeta(meta);

        menuRoute.setPermissionCode(permission.getPermissionCode());
        menuRoute.setMenuScope(permission.getMenuScope());
        menuRoute.setStatus(permission.getStatus());
        menuRoute.setSort(permission.getSort());
        menuRoute.setPermissionType(permission.getPermissionType());
        menuRoute.setParentId(permission.getParentId());
        menuRoute.setCreateTime(permission.getCreateTime());
        menuRoute.setUpdateTime(permission.getUpdateTime());

        return menuRoute;
    }

    private Result normalizeAndValidate(SysPermission permission, boolean update) {
        if (permission == null || (update && permission.getId() == null)) {
            return Result.error("权限信息不完整");
        }
        permission.setPermissionName(StrUtil.trim(permission.getPermissionName()));
        permission.setPermissionCode(StrUtil.trim(permission.getPermissionCode()));
        if (StrUtil.isBlank(permission.getPermissionName())
                || permission.getPermissionName().length() > 100) {
            return Result.error("权限名称不能为空且不能超过100个字符");
        }
        if (StrUtil.isBlank(permission.getPermissionCode())
                || permission.getPermissionCode().length() > 100
                || !permission.getPermissionCode().matches("^[A-Za-z][A-Za-z0-9:_-]*$")) {
            return Result.error("权限编码格式错误");
        }
        if (permission.getPermissionType() == null) {
            return Result.error("权限类型不能为空");
        }
        permission.setParentId(permission.getParentId() == null ? 0L : permission.getParentId());
        permission.setSort(permission.getSort() == null ? 0 : permission.getSort());
        permission.setStatus(permission.getStatus() == null ? StatusEnum.NORMAL : permission.getStatus());
        permission.setIsHide(Boolean.TRUE.equals(permission.getIsHide()));
        permission.setIsIframe(Boolean.TRUE.equals(permission.getIsIframe()));
        permission.setKeepAlive(Boolean.TRUE.equals(permission.getKeepAlive()));
        permission.setLink(StrUtil.blankToDefault(StrUtil.trim(permission.getLink()), null));
        if (permission.getPermissionType() == PermissionTypeEnum.MENU) {
            permission.setRouterAddress(StrUtil.trim(permission.getRouterAddress()));
            permission.setComponentAddress(StrUtil.trim(permission.getComponentAddress()));
            if (StrUtil.isBlank(permission.getRouterAddress())
                    || StrUtil.isBlank(permission.getComponentAddress())) {
                return Result.error("菜单必须填写路由地址和组件地址");
            }
        } else {
            if (permission.getParentId() <= 0) {
                return Result.error("按钮和API权限必须选择上级菜单");
            }
            permission.setRouterAddress(null);
            permission.setComponentAddress(null);
            permission.setIcon(null);
            permission.setLink(null);
            permission.setIsHide(false);
            permission.setIsIframe(false);
            permission.setKeepAlive(false);
        }
        if (permission.getPermissionType() != PermissionTypeEnum.MENU) permission.setMenuScope(null);
        else if (PermissionHierarchy.isDirectory(permission)) permission.setMenuScope(MenuScopeEnum.ALL);
        else if (permission.getMenuScope() == null) {
            String component = permission.getComponentAddress().replaceFirst("^/?(?:src/)?views/", "")
                    .replaceFirst("^/+", "").replaceFirst("\\.vue$", "").replaceFirst("/index$", "");
            permission.setMenuScope(List.of("dashboard/console", "system/user-center").contains(component) ? MenuScopeEnum.ALL
                    : permission.getPermissionCode().startsWith("platform:") || component.startsWith("platform/") ? MenuScopeEnum.PLATFORM : MenuScopeEnum.TENANT);
        }
        return null;
    }

}
