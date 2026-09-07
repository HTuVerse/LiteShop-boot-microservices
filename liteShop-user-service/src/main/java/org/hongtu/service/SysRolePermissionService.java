package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.SysRolePermission;
import org.hongtu.Result.Result;

import java.util.List;


/**
 * <p>
 * 角色权限关联表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
public interface SysRolePermissionService extends IService<SysRolePermission> {

    /**
     * 获取角色的权列表
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    Result listMenuTreeByRole(Long roleId);


    /**
     * 为角色分配权限
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     * @return 操作结果
     */
    Result assignPermissions(Long roleId, List<Long> permissionIds);

}
