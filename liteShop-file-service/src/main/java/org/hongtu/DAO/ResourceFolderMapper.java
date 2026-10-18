package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.ResourceFolder;

/** 资源库文件夹持久化。 */
@Mapper
public interface ResourceFolderMapper extends BaseMapper<ResourceFolder> { }
