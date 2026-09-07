package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.SysRole;
import org.hongtu.Result.Result;

import java.util.List;


/**
 * <p>
 * 系统角色表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
public interface SysRoleService extends IService<SysRole> {

    Result<List<SysRole>> getAllRoles();

    /**
     * 添加角色
     * @param role 角色信息
     * @return 操作结果
     */
    Result addRole(SysRole role);

    /**
     * 更新角色
     * @param role 角色信息
     * @return 操作结果
     */
    Result updateRole(SysRole role);

    /**
     * 删除角色
     * @param id 角色ID
     * @return 操作结果
     */
    Result deleteRole(Long id);
}
