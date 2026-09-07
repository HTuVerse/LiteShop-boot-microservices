package org.hongtu.DAO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.hongtu.DTO.UserLevelScheme;
@Mapper
public interface UserLevelSchemeMapper extends BaseMapper<UserLevelScheme> {
    @Select("SELECT id FROM user_level_scheme WHERE tenant_id IS NULL ORDER BY id FOR UPDATE")
    java.util.List<Long> lockTemplates();
    @Select("SELECT id FROM sys_tenant WHERE id=#{tenantId} FOR UPDATE")
    Long lockTenant(@Param("tenantId") Long tenantId);
    @Update("""
        UPDATE sys_user u SET u.level = COALESCE((SELECT l.level_code FROM user_level l
        WHERE l.scheme_id=#{schemeId} AND l.tenant_id=#{tenantId} AND l.status=1
        AND l.required_exp <= COALESCE(u.exp,0) ORDER BY l.required_exp DESC, l.level_code DESC LIMIT 1),0)
        WHERE u.tenant_id=#{tenantId}
        """)
    int recalculateUsers(@Param("tenantId") Long tenantId, @Param("schemeId") Long schemeId);
}
