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
import org.hongtu.enums.BooleanEnum;
import org.hongtu.restaurant.enums.PaymentModeEnum;
import org.hongtu.restaurant.enums.pickupModeEnum;
import org.hongtu.restaurant.enums.storeStatusEnum;

/**
 * <p>
 * 门店信息表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("store_info")
public class StoreInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 门店名称
     */
    private String storeName;

    /**
     * 门店编码
     */
    private String storeCode;

    /**
     * 门店地址
     */
    private String address;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 营业时间
     */
    private String businessHours;

    /**
     * 支付模式：1-线上支付，2-线下支付
     */
    private PaymentModeEnum paymentMode;

    /**
     * 取餐模式：0-送上桌，1-叫号自取
     */
    private pickupModeEnum pickupMode;

    /**
     * 是否允许预点单：0-否，1-是
     */
    private BooleanEnum allowPreOrder;

    /**
     * 预点单时间限制（分钟）
     */
    private Integer preOrderLimit;

    /**
     * 状态：0-停业，1-营业 ,2-休息中
     */
    private storeStatusEnum status;

    /**
     * 纬度（用于地图定位）
     */
    private Double latitude;

    /**
     * 打印状态
     * */
    private BooleanEnum isPrint;

    /**
     * 经度（用于地图定位）
     */
    private Double longitude;

    /**
     * 门店图片URL（多张图片用逗号分隔）
     */
    private String storeImages;

    /**
     * 门店简介
     */
    private String description;

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
