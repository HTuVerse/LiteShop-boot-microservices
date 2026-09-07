package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.TableStatusEnum;

/**
 * <p>
 * 餐桌信息表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("table_info")
public class TableInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 餐桌编号
     */
    private String tableNumber;

    /**
     * 餐桌名称
     */
    private String tableName;

    /**
     * 二维码内容
     */
    private String qrCode;

    /**
     * 二维码图片Base64
     */
    private String qrCodeImage;

    /**
     * 座位数
     */
    private Integer seats;

    /**
     * 位置描述
     */
    private String location;

    /**
     * 状态：0-空闲，1-使用中，2-预留，3-停用
     */
    private TableStatusEnum status;

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
