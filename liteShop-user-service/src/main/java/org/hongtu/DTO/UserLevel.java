package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户等级配置表
 * </p>
 *
 * @author Ht
 * @since 2025-08-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_level")
public class UserLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 等级ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long schemeId;

    /**
     * 等级编码
     */
    private Integer levelCode;

    /**
     * 等级名称
     */
    private String levelName;

    /**
     * 所需经验值
     */
    private Integer requiredExp;

    /**
     * 等级图标
     */
    private String icon;

    /**
     * 等级描述
     */
    private String description;

    /**
     * 等级权益
     */
    private String benefits;

    /**
     * 显示顺序
     */
    private Integer sort;

    /**
     * 是否启用（1-启用，0-禁用）
     */
    private Integer status;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;


}
