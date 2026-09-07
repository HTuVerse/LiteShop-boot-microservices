package org.hongtu.restaurant.DTO;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.restaurant.enums.*;
import org.hongtu.restaurant.handler.CouponTypeListTypeHandler;

/**
 * <p>
 * 餐厅订单表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "restaurant_order", autoResultMap = true)
public class RestaurantOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(value = "tenant_id", fill = FieldFill.INSERT_UPDATE)
    private Long tenantId;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 餐桌ID
     */
    private Long tableId;

    /**
     * 用户ID（可选）
     */
    private Long userId;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 就餐人数
     */
    private Integer diningCount;

    /**
     * 订单类型 0-在线点餐 ，1-预点餐，2-门店自提
     */
    private SceneTypeEnum orderType;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 整单折扣率（例如：0.8表示8折，1.0表示无折扣）
     */
    private BigDecimal discountRate;

    /**
     * 实际支付金额
     */
    private BigDecimal actualAmount;

    /**
     * 支付模式：1-线上支付，2-线下支付
     */
    private PaymentModeEnum paymentMode;

    /**
     * 取餐码（叫号自取模式使用）
     */
    private String pickupCode;

    /**
     * 支付状态：0-未支付，1-已支付，2-部分退款，3-全额退款
     */
    private PaymentStatusEnum paymentStatus;

    /**
     * 支付方式：1-微信，2-支付宝，3-现金，4-银行卡
     */
    private PaymentTypeEnum paymentType;

    /**
     * 支付时间
     */
    private LocalDateTime paymentTime;

    /**
     * 支付流水号
     */
    private String paymentNo;

    /**
     * 订单状态：1-已下单，2-已接单，3-制作中，4-制作完成，5-已完成，6-已取消
     */
    private OrderStatusEnum status;

    /**
     * 订单备注
     */
    private String note;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 取消时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime cancelTime;

    /**
     * 结束用餐时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime diningEndTime;

    /**
     * 使用的用户优惠券ID列表（逗号分隔，如 "1,2,3"）
     * 对应 user_coupon 表的 id
     */
    private String userCouponIds;

    /**
     * 使用的优惠券类型列表
     * @see CouponTypeEnum
     */
    @TableField(typeHandler = CouponTypeListTypeHandler.class)
    private List<CouponTypeEnum> couponTypes;

    /**
     * 服务费
     */
    private BigDecimal serviceFee;

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
