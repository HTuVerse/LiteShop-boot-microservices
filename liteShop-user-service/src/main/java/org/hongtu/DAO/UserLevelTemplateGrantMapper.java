package org.hongtu.DAO;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import org.hongtu.DTO.SysTenant;
import org.hongtu.DTO.UserLevelScheme;
import java.util.List;

/** Explicit predicates permit platform allocation and tenant-scoped template discovery. */
@Mapper
public interface UserLevelTemplateGrantMapper {
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT s.* FROM user_level_scheme s WHERE s.tenant_id IS NULL AND s.status=1
        AND (s.is_default=1 OR EXISTS(SELECT 1 FROM user_level_template_tenant g
            WHERE g.scheme_id=s.id AND g.tenant_id=#{tenantId}))
        ORDER BY s.is_default DESC,s.id ASC
        """)
    List<UserLevelScheme> available(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM user_level_template_tenant WHERE scheme_id=#{schemeId} AND tenant_id=#{tenantId}")
    int count(@Param("schemeId") Long schemeId,@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT t.id,t.tenant_name,t.tenant_code,t.status FROM sys_tenant t
        JOIN user_level_template_tenant g ON g.tenant_id=t.id
        WHERE g.scheme_id=#{schemeId} ORDER BY t.id
        """)
    List<SysTenant> assignedTenants(@Param("schemeId") Long schemeId);

    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM user_level_template_tenant WHERE scheme_id=#{schemeId}")
    int deleteForTemplate(@Param("schemeId") Long schemeId);

    @InterceptorIgnore(tenantLine = "true")
    @Insert({"<script>","INSERT INTO user_level_template_tenant(scheme_id,tenant_id) VALUES",
        "<foreach collection='tenantIds' item='tenantId' separator=','>(#{schemeId},#{tenantId})</foreach>","</script>"})
    int insertAll(@Param("schemeId") Long schemeId,@Param("tenantIds") List<Long> tenantIds);
}
