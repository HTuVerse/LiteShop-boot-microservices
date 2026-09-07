package org.hongtu.DAO;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.hongtu.DTO.UserLevel;
import org.hongtu.tenant.TenantContext;
import java.util.List;

@Mapper
public interface UserLevelMapper extends BaseMapper<UserLevel> {
    // The tenant interceptor is bypassed only for these explicitly scoped reads, allowing
    // a NULL-tenant platform default when this tenant has no enabled active scheme.
    String EFFECTIVE_LEVELS = """
        SELECT l.* FROM user_level l JOIN user_level_scheme s ON l.scheme_id=s.id
        AND (l.tenant_id=s.tenant_id OR (l.tenant_id IS NULL AND s.tenant_id IS NULL))
        WHERE s.id=COALESCE(
          (SELECT id FROM user_level_scheme WHERE tenant_id=#{tenantId} AND is_active=1 AND status=1),
          (SELECT id FROM user_level_scheme WHERE tenant_id IS NULL AND is_default=1 AND status=1))
        """;

    @InterceptorIgnore(tenantLine = "true")
    @Select(EFFECTIVE_LEVELS + " AND l.status=1 AND l.required_exp <= #{exp} ORDER BY l.required_exp DESC, l.level_code DESC LIMIT 1")
    UserLevel getLevelByExpScoped(@Param("exp") Integer exp, @Param("tenantId") Long tenantId);
    default UserLevel getLevelByExp(Integer exp) { return getLevelByExpScoped(exp, TenantContext.getTenantIdOrNull()); }

    @InterceptorIgnore(tenantLine = "true")
    @Select(EFFECTIVE_LEVELS + " AND l.status=1 AND l.level_code=#{levelCode} LIMIT 1")
    UserLevel getLevelByLevelCodeScoped(@Param("levelCode") Integer levelCode, @Param("tenantId") Long tenantId);
    default UserLevel getLevelByLevelCode(Integer levelCode) { return getLevelByLevelCodeScoped(levelCode, TenantContext.getTenantIdOrNull()); }

    @InterceptorIgnore(tenantLine = "true")
    @Select(EFFECTIVE_LEVELS + " AND l.status=1 AND l.required_exp > (SELECT current_level.required_exp FROM user_level current_level WHERE current_level.scheme_id=s.id AND (current_level.tenant_id=s.tenant_id OR (current_level.tenant_id IS NULL AND s.tenant_id IS NULL)) AND current_level.level_code=#{currentLevelCode}) ORDER BY l.required_exp ASC, l.level_code ASC LIMIT 1")
    UserLevel getNextLevelScoped(@Param("currentLevelCode") Integer currentLevelCode, @Param("tenantId") Long tenantId);
    default UserLevel getNextLevel(Integer currentLevelCode) { return getNextLevelScoped(currentLevelCode, TenantContext.getTenantIdOrNull()); }

    @InterceptorIgnore(tenantLine = "true")
    @Select(EFFECTIVE_LEVELS + " ORDER BY l.required_exp ASC, l.sort ASC")
    List<UserLevel> listLevelsScoped(@Param("tenantId") Long tenantId);
    default List<UserLevel> listLevels() { return listLevelsScoped(TenantContext.getTenantIdOrNull()); }

    @InterceptorIgnore(tenantLine = "true")
    @Select(EFFECTIVE_LEVELS + " AND l.status=1 ORDER BY l.required_exp ASC, l.sort ASC")
    List<UserLevel> listEnabledLevelsScoped(@Param("tenantId") Long tenantId);
    default List<UserLevel> listEnabledLevels() { return listEnabledLevelsScoped(TenantContext.getTenantIdOrNull()); }
}
