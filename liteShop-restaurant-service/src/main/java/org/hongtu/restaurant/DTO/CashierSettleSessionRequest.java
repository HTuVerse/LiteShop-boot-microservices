package org.hongtu.restaurant.DTO;

import lombok.Data;
import org.hongtu.restaurant.enums.PaymentTypeEnum;

import java.math.BigDecimal;

/**
 * 收银台-桌位结算请求 DTO
 * 用于桌位会话场景：对会话当前订单做线下支付并结束会话、释放桌位。
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
     * 支付方式：3-现金，4-银行卡，6-余额支付
     */
    private PaymentTypeEnum paymentType;

    /**
     * 实收金额（选填；不传则用订单应付金额，传了则用于找零与记录）
     */
    private BigDecimal actualAmount;

    /**
     * 用户ID（余额支付时必填）
     */
    private Long userId;

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
