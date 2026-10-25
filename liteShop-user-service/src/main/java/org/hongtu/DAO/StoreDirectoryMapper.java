package org.hongtu.DAO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.DTO.StoreDirectory;

import java.util.List;

/** 门店目录投影的幂等更新与租户内查询。 */
@Mapper
public interface StoreDirectoryMapper {
    @Insert("""
            INSERT INTO sys_store_directory
              (tenant_id, store_id, store_name, active, sync_version, updated_at)
            VALUES (#{tenantId}, #{storeId}, #{storeName}, #{active}, #{syncVersion}, NOW())
            ON DUPLICATE KEY UPDATE
              store_name = IF(sync_version <= VALUES(sync_version), VALUES(store_name), store_name),
              active = IF(sync_version <= VALUES(sync_version), VALUES(active), active),
              updated_at = IF(sync_version <= VALUES(sync_version), NOW(), updated_at),
              sync_version = GREATEST(sync_version, VALUES(sync_version))
            """)
    int upsert(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
               @Param("storeName") String storeName, @Param("active") boolean active,
               @Param("syncVersion") long syncVersion);

    @Select("""
            <script>
            SELECT tenant_id AS tenantId, store_id AS storeId, store_name AS storeName,
                   active, sync_version AS syncVersion
            FROM sys_store_directory WHERE tenant_id = #{tenantId} AND active = 1
              AND store_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<StoreDirectory> activeByIds(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);
}
