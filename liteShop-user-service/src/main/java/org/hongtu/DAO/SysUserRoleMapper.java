package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.DTO.SysUserRole;

import java.util.List;

/**
 * <p>
 * 用户角色关联表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 根据用户ID查询角色ID
     *
     * @param userId 用户ID
     * @return 角色ID
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    Long getRoleIdByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询所有角色编码（支持多角色）
     *
     * @param userId 用户ID
     * @return 角色编码列表（按角色ID升序排列）
     */
    @Select("SELECT r.role_code " +
            "FROM sys_user_role ur " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 " +
            "ORDER BY r.id ASC") // 按角色ID排序保证结果有序
    List<String> getRoleCodesByUserId(@Param("userId") Long userId);


    /**
     * 根据用户ID查询所有角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

}
