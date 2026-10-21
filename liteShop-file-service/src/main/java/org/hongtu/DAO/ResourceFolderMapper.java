package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.ResourceFolder;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/** 资源库文件夹持久化。 */
@Mapper
public interface ResourceFolderMapper extends BaseMapper<ResourceFolder> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM resource_folder WHERE id=#{id} LIMIT 1")
    ResourceFolder findAcrossTenants(@Param("id") Long id);
}
