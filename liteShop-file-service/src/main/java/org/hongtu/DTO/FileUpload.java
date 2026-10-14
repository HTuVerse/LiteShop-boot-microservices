package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.StorageTypeEnum;

import java.io.Serializable;

/**
 * <p>
 * 文件上传
 * </p>
 *
 * @author Honghong
 * @since 2025-07-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("file_upload")
public class FileUpload implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文件原始名称
     */
    private String originalFileName;

    /**
     * 文件大小(字节)
     */
    private Long size;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件访问URL
     */
    private String url;

    /**
     * 存储类型：0本地，1对象存储
     */
    private StorageTypeEnum storageType;


}
