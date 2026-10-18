package org.hongtu.config.DAO;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.hongtu.config.DTO.FileStorageProfile;

/** 网站配置表中平台服务记录的读写。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface PlatformServiceConfigMapper {
    @Select("SELECT config_value FROM sys_website_config WHERE id = #{id}")
    String read(@Param("id") int id);

    @Update("UPDATE sys_website_config SET config_value = #{json}, revision = revision + 1, " +
            "updated_by = #{userId}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND revision = #{expected}")
    int publish(@Param("id") int id, @Param("json") String json,
                @Param("expected") long expected, @Param("userId") long userId);

    /** 保存配置快照供历史资源读取。 */
    @Insert("INSERT INTO file_storage_profile (config_json) VALUES (#{configJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProfile(FileStorageProfile profile);

    /** 按文件记录绑定的快照读取原存储配置。 */
    @Select("SELECT config_json FROM file_storage_profile WHERE id = #{id}")
    String readProfile(@Param("id") Long id);
}
