package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.SysPermission;
import org.hongtu.Result.Result;
import org.hongtu.VO.MenuRouteVO;
import org.hongtu.VO.SysPermissionVO;

import java.util.List;


/**
 * <p>
 * 系统权限表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
public interface SysPermissionService extends IService<SysPermission> {


    /**
     * 获取权限列表（树形结构）
     * @return 权限树
     */
    Result getPermissionTree();

    /**
     * 添加权限
     * @param permission 权限信息
     * @return 操作结果
     */
    Result addPermission(SysPermission permission);

    /**
     * 更新权限
     * @param permission 权限信息
     * @return 操作结果
     */
    Result updatePermission(SysPermission permission);

    Result updateHierarchy(org.hongtu.DTO.PermissionHierarchyRequest request);

    /**
     * 删除权限
     * @param id 权限ID
     * @return 操作结果
     */
    Result deletePermission(Long id);

    /**
     * 构建权限树
     * @param permissions 权限列表
     * @param parentId 父权限ID
     * @return 权限树
     */
    List<SysPermissionVO> buildPermissionTree(List<SysPermission> permissions, Long parentId);


    /**
     * 获取用户菜单路由
     * @param userId 用户ID
     * @return 菜单路由
     */
    Result getUserMenuRoutes(Long userId);

    List<MenuRouteVO> buildMenuRouteTree(List<SysPermission> permissions, Long parentId);

    /**
     * 转换为菜单路由
     * @param permission 权限信息
     * @return 菜单路由
     */
    MenuRouteVO convertToMenuRoute(SysPermission permission);
}
