package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.FileUpload;

/**
 * <p>
 * 文件上传 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-07-06
 */
@Mapper
public interface FileUploadMapper extends BaseMapper<FileUpload> {

}
