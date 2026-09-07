package org.hongtu.restaurant.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.enums.PaymentTypeEnum;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付状态查询结果 VO
 * 用于前端轮询查询订单支付状态
 *
 * @author Ht
 * @since 2025-11-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 基本订单信息 ====================
    
    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 实际支付金额
     */
    private BigDecimal actualAmount;

    // ==================== 订单状态 ====================

    /**
     * 订单状态（枚举对象）
     */
    private OrderStatusEnum orderStatus;

    /**
     * 订单状态码
     */
    private Integer orderStatusCode;

    /**
     * 订单状态描述
     */
    private String orderStatusDesc;

    // ==================== 支付状态（详细） ====================

    /**
     * 支付状态（枚举对象）
     */
    private PaymentStatusEnum paymentStatus;

    /**
     * 支付状态码
     */
    private Integer paymentStatusCode;

    /**
     * 支付状态描述
     */
    private String paymentStatusDesc;

    // ==================== 支付方式 ====================

    /**
     * 支付方式（枚举对象）
     */
    private PaymentTypeEnum paymentType;

    /**
     * 支付方式码
     */
    private Integer paymentTypeCode;

    /**
     * 支付方式描述
     */
    private String paymentTypeDesc;

    // ==================== 支付交易信息 ====================

    /**
     * 支付交易号（微信支付单号/支付宝交易号）
     */
    private String paymentNo;

    // ==================== 时间信息 ====================

    /**
     * 订单创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 支付完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime paymentTime;

    /**
     * 取消时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime cancelTime;

    // ==================== 取消原因 ====================

    /**
     * 取消原因
     */
    private String cancelReason;

    // ==================== 简化的状态标识（用于前端快速判断） ====================

    /**
     * 是否已支付
     */
    private Boolean isPaid;

    /**
     * 是否已取消
     */
    private Boolean isCancelled;

    /**
     * 是否支付超时
     */
    private Boolean isTimeout;

    /**
     * 是否支付失败
     */
    private Boolean isFailed;

    /**
     * 是否订单关闭
     */
    private Boolean isClosed;

    // ==================== 操作权限标识 ====================

    /**
     * 是否可以继续支付
     * true：可以支付
     * false：不能支付（已支付/已取消/已完成）
     */
    private Boolean canPay;

    /**
     * 是否可以取消订单
     * true：可以取消
     * false：不能取消（已支付/已取消/已完成）
     */
    private Boolean canCancel;

    // ==================== 扩展信息（可选） ====================

    /**
     * 支付等待时长（秒）
     * 从创建订单到当前的时长
     */
    private Long waitingSeconds;

    /**
     * 是否已超过超时时间（30分钟）
     */
    private Boolean isExpired;
}

