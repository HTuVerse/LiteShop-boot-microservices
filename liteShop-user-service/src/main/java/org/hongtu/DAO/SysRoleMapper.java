package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.DTO.SysPermission;
import org.hongtu.DTO.SysRole;


import java.util.List;

/**
 * <p>
 * 系统角色表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户ID查询角色编码集合
     *
     * @param userId 用户ID
     * @return 角色编码集合
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据角色编码查询角色
     *
     * @param roleCode 角色编码
     * @return 角色ID集合
     */
    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode} LIMIT 1")
    SysRole selectRoleByRoleCode(@Param("roleCode") String roleCode);


    /**
     * 查询角色拥有的所有权限列表Id
     *
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色拥有的所有权限详情列表
     *
     * @param roleId 角色ID
     * @return 权限详情列表
     */
    List<SysPermission> selectPermissionsByRoleId(@Param("roleId") Long roleId);
}
