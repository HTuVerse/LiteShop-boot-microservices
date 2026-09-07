package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.DTO.SysPermission;


import java.util.List;

/**
 * <p>
 * 系统权限表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 根据用户ID查询权限码集合
     * 通过三表关联（用户-角色-权限）查询用户拥有的权限
     *
     * @param userId 用户ID
     * @return 权限码集合
     */

    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);


    /**
     * 查询所有权限列表
     *
     * @return 权限列表
     */
    @Select("SELECT * FROM sys_permission ORDER BY sort, id")
    List<SysPermission> selectAllPermissions();

    @Select("SELECT * FROM sys_permission ORDER BY id FOR UPDATE")
    List<SysPermission> selectHierarchyForUpdate();

    @org.apache.ibatis.annotations.Update("UPDATE sys_permission SET parent_id = #{parentId}, sort = #{sort}, "
            + "router_address = #{path}, update_time = NOW() WHERE id = #{id}")
    int updateHierarchy(@Param("id") Long id, @Param("parentId") Long parentId,
                        @Param("sort") Integer sort, @Param("path") String path);

    /**
     * 根据父ID查询子权限
     *
     * @param parentId 父权限ID
     * @return 子权限列表
     */
    @Select("SELECT * FROM sys_permission WHERE parent_id = #{parentId} ORDER BY sort, id")
    List<SysPermission> selectPermissionsByParentId(@Param("parentId") Long parentId);

    /**
     * 根据权限类型查询权限列表
     *
     * @param permissionType 权限类型
     * @return 权限列表
     */
    @Select("SELECT * FROM sys_permission WHERE permission_type = #{permissionType} ORDER BY sort, id")
    List<SysPermission> selectPermissionsByType(@Param("permissionType") Integer permissionType);

    /**
     * 检查权限编码是否存在
     *
     * @param permissionCode 权限编码
     * @return 存在的数量
     */
    @Select("SELECT COUNT(*) FROM sys_permission WHERE permission_code = #{permissionCode}")
    Integer checkPermissionCodeExists(@Param("permissionCode") String permissionCode);

    /**
     * 检查是否有子权限
     *
     * @param parentId 父权限ID
     * @return 子权限数量
     */
    @Select("SELECT COUNT(*) FROM sys_permission WHERE parent_id = #{parentId}")
    Integer countChildrenByParentId(@Param("parentId") Long parentId);


    /**
     * 根据用户ID查询菜单权限列表
     * 只查询类型为菜单的权限，并且状态为启用的
     *
     * @param userId 用户ID
     * @return 菜单权限列表
     */
    List<SysPermission> selectMenuPermissionsByUserId(@Param("userId") Long userId);

}
