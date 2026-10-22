package org.hongtu.restaurant.DTO;

import lombok.Data;
import org.hongtu.restaurant.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收银台-桌位结算请求 DTO
 * 用于桌位会话场景：前台收款后结束会话、释放桌位。
 * 支持收银时传入整单折扣、备注，会写回订单。
 *
 * @author Ht
 */
@Data
public class CashierSettleSessionRequest {

    /**
     * 会话编号（必填）
     */
    private String sessionNo;

    /**
     * 支付方式：1-微信，2-支付宝，3-现金，4-银行卡，6-余额支付
     */
    private PaymentTypeEnum paymentType;

    /** 微信/支付宝付款码，由前台扫码枪读取。 */
    private String authCode;

    /**
     * 客户端实收金额（仅现金找零使用，不能覆盖服务端计算的订单应付金额）
     */
    private BigDecimal actualAmount;

    /**
     * 用户ID（余额支付时必填）
     */
    private Long userId;

    private List<Long> userCouponIds;

    /**
     * 整单折扣率（选填，结算时收银可打折扣。例如 0.8 表示 8 折，1.0 表示无折扣）
     */
    private BigDecimal discountRate;

    /**
     * 订单备注（选填，结算时收银可填，会更新到订单）
     */
    private String note;

    /**
     * 是否打印小票（默认 true）
     */
    private Boolean printReceipt = true;
}
