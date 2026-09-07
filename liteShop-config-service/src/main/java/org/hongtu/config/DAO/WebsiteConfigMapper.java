package org.hongtu.config.DAO;

import org.apache.ibatis.annotations.*;

@Mapper
public interface WebsiteConfigMapper {
    @Select("SELECT config_value FROM sys_website_config WHERE id = 1")
    String read();

    @Update("UPDATE sys_website_config SET config_value = #{json}, revision = revision + 1, " +
            "updated_by = #{userId}, updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND revision = #{revision}")
    int publish(@Param("json") String json, @Param("revision") long revision, @Param("userId") long userId);
}
