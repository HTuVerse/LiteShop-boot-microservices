package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/** 租户资源库文件夹，parentId 为空时位于根目录。 */
@Data
@TableName("resource_folder")
public class ResourceFolder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Long tenantId;
    /** 父文件夹 ID，空表示根目录。 */
    private Long parentId;
    @TableField(exist = false)
    private Integer myPermissions;
    @TableField(exist = false)
    private Boolean navigationOnly;
}
