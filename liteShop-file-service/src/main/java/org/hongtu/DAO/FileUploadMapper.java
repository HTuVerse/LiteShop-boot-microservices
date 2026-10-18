package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.FileUpload;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

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
    /** 公开入口和私有授权均需跨租户读取元数据，权限由 Controller 单独校验。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM file_upload WHERE id = #{id} LIMIT 1")
    FileUpload findAcrossTenants(@Param("id") Long id);

    /** 公开静态访问按随机 Key 定位历史本地配置，禁止返回私有资源。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM file_upload WHERE storage_type = 0 AND visibility = 'PUBLIC' " +
            "AND (storage_key = #{key} OR (storage_key IS NULL AND url LIKE CONCAT('%/', #{key}))) LIMIT 1")
    FileUpload findPublicLocal(@Param("key") String key);
}
