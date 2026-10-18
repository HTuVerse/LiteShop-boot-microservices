package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 平台资源库的平级分类文件夹。 */
@Data
@TableName("resource_folder")
public class ResourceFolder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Long tenantId;
}
