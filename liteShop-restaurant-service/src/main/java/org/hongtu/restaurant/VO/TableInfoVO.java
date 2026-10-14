package org.hongtu.restaurant.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.restaurant.enums.PaymentModeEnum;
import org.hongtu.restaurant.enums.TableStatusEnum;
import org.hongtu.restaurant.enums.storeStatusEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 桌位详情视图对象（包含店铺信息）
 * </p>
 *
 * @author Ht
 * @since 2025-11-21
 */
@Data
public class TableInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 桌位信息 ==========
    /**
     * 桌位主键ID
     */
    private Long id;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 店铺名称
     */
    private String storeName;

    /**
     * 餐桌编号
     */
    private String tableNumber;

    /**
     * 餐桌名称
     */
    private String tableName;

    /**
     * 当前进行中的会话编号（有值表示该桌位正在使用中）
     */
    private String sessionNo;

    /**
     * 用餐时长（分钟）
     */
    private Long durationMinutes;

    /**
     * 二维码内容
     */
    private String qrCode;

    /**
     * 二维码图片Base64
     */
    private String qrCodeImage;

    /** 匿名扫码加入桌台时必须回传的服务端签名票据。 */
    private String scanToken;

    /**
     * 座位数
     */
    private Integer seats;

    /**
     * 位置描述
     */
    private String location;

    /**
     * 桌位状态：0-空闲，1-使用中，2-预留，3-停用
     */
    private TableStatusEnum status;

    /** 从预约记录表动态查询的最近一条待到店预约，不是 table_info 的持久化字段。 */
    private TableReservationVO nextReservation;

    /**
     * 桌位创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 桌位更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

}

