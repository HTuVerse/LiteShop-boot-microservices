package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店分组，用于按区域或经营策略批量发布菜单配置。
 */
@Data
@Accessors(chain = true)
@TableName("store_group")
public class StoreGroup implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String groupCode;
    private String groupName;
    private String description;

    /** 状态：0停用，1启用。 */
    private Integer status;

    /** 逻辑删除：0正常，1删除。 */
    private Integer deleted;

    @TableField(exist = false)
    private List<Long> storeIds;

    @TableField(exist = false)
    private Integer memberCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
