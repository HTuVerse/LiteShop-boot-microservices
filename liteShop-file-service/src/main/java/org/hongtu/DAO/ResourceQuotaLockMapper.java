package org.hongtu.DAO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 用数据库行锁串行化同一租户的并发上传容量校验。 */
@Mapper
public interface ResourceQuotaLockMapper {
    @Insert("INSERT IGNORE INTO resource_quota_lock (tenant_id) VALUES (#{tenantId})")
    int ensure(@Param("tenantId") Long tenantId);

    @Select("SELECT tenant_id FROM resource_quota_lock WHERE tenant_id = #{tenantId} FOR UPDATE")
    Long lock(@Param("tenantId") Long tenantId);
}
