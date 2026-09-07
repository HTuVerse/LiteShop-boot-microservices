package org.hongtu.config.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.config.DTO.AppConfig;

/**
 * 系统配置 Mapper 接口
 *
 * @author Ht
 */
@Mapper
public interface AppConfigMapper extends BaseMapper<AppConfig> {
    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO sys_config (id,tenant_id,config_key,config_value,config_group,config_type,config_name,editable,created_at,updated_at)
        VALUES (#{id},#{tenantId},#{key},#{value},#{group},#{type},#{name},1,NOW(),NOW())
        ON DUPLICATE KEY UPDATE config_key=config_key
        """)
    int insertDefault(@org.apache.ibatis.annotations.Param("id") Long id,@org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
        @org.apache.ibatis.annotations.Param("key") String key,@org.apache.ibatis.annotations.Param("value") String value,
        @org.apache.ibatis.annotations.Param("group") int group,@org.apache.ibatis.annotations.Param("type") int type,
        @org.apache.ibatis.annotations.Param("name") String name);


}
