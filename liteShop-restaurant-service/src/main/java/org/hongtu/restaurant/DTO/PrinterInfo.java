package org.hongtu.restaurant.DTO;

import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.restaurant.enums.PrinterStatusEnum;
import org.hongtu.restaurant.enums.PrinterTypeEnum;
import org.hongtu.restaurant.enums.PaperWidthEnum;
import org.hongtu.restaurant.enums.printerBrandEnum;

/**
 * <p>
 * 飞鹅打印机信息表
 * </p>
 *
 * @author Ht
 * @since 2025-01-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("printer_info")
public class PrinterInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 打印机编号（飞鹅云打印机SN）
     */
    private String sn;

    /**
     * 打印机识别码（飞鹅云打印机KEY）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JSONField(serialize = false)
    private String printerKey;

    /**
     * 打印机备注名称（如：前台打印机、后厨打印机）
     */
    private String remark;

    /**
     * 流量卡号码（选填）
     */
    private String cardNum;

    /**
     * 关联的门店ID
     */
    private Long storeId;

    /**
     * 小票模板id
     */
    private Long templateId;

    /** 实际装纸宽度：0-58mm，1-80mm；打印排版以设备宽度为准。 */
    private PaperWidthEnum paperWidth;

    /**
     * 打印机品牌
     * */
    private printerBrandEnum printerBrand;

    /**
     * 打印机类型：1-小票机，2-标签机
     */
    private PrinterTypeEnum printerType;

    /**
     * 打印机状态：0-离线，1-在线，2-缺纸
     */
    private PrinterStatusEnum status;

    /**
     * 最后在线时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastOnlineTime;

    /**
     * 是否为默认打印机：0-否，1-是
     */
    private BooleanEnum isDefault;

    /**
     * 是否启用：0-禁用，1-启用
     */
    private StatusEnum enabled;

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
