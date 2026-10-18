package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.StorageTypeEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

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

    /** 历史访问 URL；新文件不持久化该字段，接口响应时派生。 */
    private String url;

    /** 存储驱动：0 Local、1 七牛、2 MinIO、3 阿里云 OSS。 */
    private StorageTypeEnum storageType;

    /** 所属商户，平台上传使用 0。 */
    private Long tenantId;

    /** 上传用户 ID。 */
    private Long uploaderId;

    /** 资源库分类文件夹 ID，未分类为空。 */
    private Long folderId;

    /** 对象 Key 或本地相对路径。 */
    private String storageKey;

    /** 存储配置快照 ID，切换默认驱动后仍使用原配置。 */
    private Long storageProfileId;

    /** 实际存储桶；本地文件为 public 或 private 目录标识。 */
    private String bucket;

    /** PUBLIC 可匿名访问；PRIVATE 必须授权。 */
    private String visibility;

    /** 文件扩展名。 */
    private String fileExtension;

    /** 最后修改时间，由数据库维护。 */
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    /** 上传时间，由数据库填充。 */
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;


}
