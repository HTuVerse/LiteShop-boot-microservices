package org.hongtu.config.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.config.enums.BannerPositionEnum;
import org.hongtu.enums.BooleanEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 轮播图配置表
 *
 * @author Ht
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_banner")
public class Banner implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片地址
     */
    private String imageUrl;

    /** 资源库图片 ID；存在时优先由文件服务派生展示地址。 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long imageResourceId;

    /**
     * 跳转链接（可选）
     */
    private String linkUrl;

    /**
     * 跳转类型：1-小程序页面 2-H5链接 3-不跳转
     */
    private Integer linkType;

    /**
     * 轮播图位置
     */
    private BannerPositionEnum position;

    /**
     * 排序值，数字越小越靠前
     */
    private Integer sortOrder;

    /**
     * 状态：0-禁用 1-启用
     */
    private BooleanEnum status;

    /**
     * 开始时间（可选，用于定时展示）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 结束时间（可选，用于定时展示）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /**
     * 备注
     */
    private String remark;

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
