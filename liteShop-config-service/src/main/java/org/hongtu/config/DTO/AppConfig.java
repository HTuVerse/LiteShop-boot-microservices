package org.hongtu.config.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.config.enums.ConfigGroupEnum;
import org.hongtu.config.enums.ConfigTypeEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置表（键值对形式，灵活扩展）
 *
 * @author Ht
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_config")
public class AppConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 配置键（唯一）
     */
    private String configKey;

    /**
     * 配置值（可存JSON）
     */
    private String configValue;

    /**
     * 配置类型
     */
    private ConfigTypeEnum configType;

    /**
     * 配置分组
     */
    private ConfigGroupEnum configGroup;

    /**
     * 配置名称（中文说明）
     */
    private String configName;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 是否允许修改：0-否 1-是
     */
    private Integer editable;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
