package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.SysUserStore;

@Mapper
public interface SysUserStoreMapper extends BaseMapper<SysUserStore> {
    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT u.id AS user_id, u.username, u.nickname, u.phone,
          EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id
                 WHERE ur.user_id=u.id AND r.status=1 AND r.role_code='ADMIN') AS all_stores
        FROM sys_user u WHERE u.tenant_id=#{tenantId}
        <if test="keyword != null and keyword != ''">
          AND (u.username LIKE CONCAT('%',#{keyword},'%') OR u.nickname LIKE CONCAT('%',#{keyword},'%')
            OR u.phone LIKE CONCAT('%',#{keyword},'%') OR CAST(u.id AS CHAR)=#{keyword})
        </if>
        <if test="storeId != null">
          AND (EXISTS(SELECT 1 FROM sys_user_store us WHERE us.user_id=u.id AND us.store_id=#{storeId})
            OR EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.status=1 AND r.role_code='ADMIN'))
        </if>
        <if test="authorized != null">
          AND (EXISTS(SELECT 1 FROM sys_user_store us WHERE us.user_id=u.id)
            OR EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.status=1 AND r.role_code='ADMIN')) = #{authorized}
        </if>
        ORDER BY u.id DESC
        </script>
        """)
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<org.hongtu.VO.UserStoreAuthorizationVO> authorizationPage(
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<org.hongtu.VO.UserStoreAuthorizationVO> page,
        @org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
        @org.apache.ibatis.annotations.Param("keyword") String keyword,
        @org.apache.ibatis.annotations.Param("storeId") Long storeId,
        @org.apache.ibatis.annotations.Param("authorized") Boolean authorized);

}
