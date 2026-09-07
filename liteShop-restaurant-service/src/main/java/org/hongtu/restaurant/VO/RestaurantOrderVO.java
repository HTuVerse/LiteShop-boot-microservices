package org.hongtu.restaurant.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.restaurant.enums.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 餐厅订单VO（包含订单明细）
 *
 * @author Ht
 * @since 2025-10-28
 */
@Data
public class RestaurantOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 门店名称
     */
    private String storeName;

    /**
     * 门店地址
     */
    private String address;

    /**
     * 门店电话
     */
    private String phone;

    /**
     * 营业时间
     */
    private String businessHours;

    /**
     * 取餐模式：0-送上桌，1-叫号自取
     */
    private pickupModeEnum pickupMode;

    /**
     * 就餐人数
     * */
    private Integer diningCount;

    /**
     * 餐桌ID
     */
    private Long tableId;

    /**
     * 餐桌名称
     */
    private String tableName;

    /**
     * 用户ID（可选）
     */
    private Long userId;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 会话状态
     */
    private SessionStatusEnum sessionStatus;

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
     * 实际支付金额
     */
    private BigDecimal actualAmount;

    /**
     * 支付模式：1-线上支付，2-线下支付
     */
    private PaymentModeEnum paymentMode;

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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
     * 开始用餐时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime diningStartTime;

    /**
     * 结束用餐时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime diningEndTime;

    /**
     * 使用的用户优惠券ID列表（逗号分隔）
     */
    private String userCouponIds;

    /**
     * 使用的优惠券类型列表
     * @see CouponTypeEnum
     */
    private List<CouponTypeEnum> couponTypes;

    /**
     * 使用的优惠券名称列表（展示用，逗号分隔）
     */
    private String couponNames;

    /**
     * 服务费
     */
    private BigDecimal serviceFee;

    /**
     * 取餐码（叫号自取模式使用）
     */
    private String pickupCode;

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

    /**
     * 订单明细列表
     */
    private List<OrderItemVO> items;

    /**
     * 订单明细VO
     */
    @Data
    public static class OrderItemVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 主键ID
         */
        private Long id;

        /**
         * 订单ID
         */
        private Long orderId;

        /**
         * 菜品ID
         */
        private Long dishId;

        /**
         * 分类id
         */
        private Long categoryId;

        /**
         * 菜品名称
         */
        private String dishName;

        /**
         * 菜品主图
         */
        private String mainPic;

        /**
         * 单价
         */
        private BigDecimal price;

        /**
         * 数量
         */
        private Integer quantity;

        /**
         * 优惠金额
         */
        private BigDecimal discountPrice;

        /**
         * 小计
         */
        private BigDecimal totalPrice;

        /**
         * 选择的规格
         */
        private String specOptionsText;

        /**
         * 批次号（第几次下单，1表示首次，2表示第二次加菜）
         */
        private Integer batchNo;

        /**
         * 加菜时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime addTime;

        /**
         * 备注（口味要求等）
         */
        private String note;

        /**
         * 是否赠菜（0-否，1-是）
         */
        private Integer isGift;

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
}

