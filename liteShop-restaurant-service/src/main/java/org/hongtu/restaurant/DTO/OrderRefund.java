package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.hongtu.restaurant.enums.RefundStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单退款单。渠道未成功前不改变订单支付状态和库存。
 */
@Data
@TableName("order_refund")
public class OrderRefund {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long orderId;
    /** 原商户订单号。 */
    private String orderNo;
    /** 服务端退款单号，作为渠道幂等号。 */
    private String refundNo;
    /** 客户端退菜幂等键。 */
    private String idempotencyKey;
    /** 原支付方式。 */
    private PaymentTypeEnum channel;
    /** 退款金额，单位：元。 */
    private BigDecimal amount;
    /** 退款状态。 */
    private RefundStatusEnum status;
    /** 渠道退款单号。 */
    private String channelRefundId;
    private String reason;
    /** 发起退菜的操作人，供异步渠道回调审计。 */
    private Long operatorId;
    private String operatorName;
    /** 渠道异常后的自动重试次数。 */
    private Integer retryCount;
    /** 下次允许对账的时间，避免渠道故障时持续请求。 */
    private LocalDateTime nextRetryAt;
    /** 超过自动重试阈值后转人工核对，异步回调仍可继续入账。 */
    private Boolean reviewRequired;
    /** 最近一次对账失败原因。 */
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
