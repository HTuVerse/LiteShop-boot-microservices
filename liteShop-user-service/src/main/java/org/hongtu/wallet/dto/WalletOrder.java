package org.hongtu.wallet.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 充值订单及下单时的档位金额快照。 */
@Data
public class WalletOrder {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long planId;
    private BigDecimal amount;
    private BigDecimal bonus;
    /** UNPAID、PAID、SUCCESS、CLOSED。 */
    private String status;
    private String requestKey;
    private String outTradeNo;
    /** 首次发起的支付渠道：WECHAT 或 ALIPAY，防止跨渠道重复支付。 */
    private String paymentChannel;
    private String paymentTxnId;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
