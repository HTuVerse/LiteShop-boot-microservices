package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.hongtu.DTO.SysRolePermission;

/**
 * <p>
 * 角色权限关联表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
    @Delete("DELETE FROM sys_role_permission WHERE permission_id = #{permissionId}")
    int deleteByPermissionId(@Param("permissionId") Long permissionId);
}
