package org.hongtu.restaurant.payment;

import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.enums.RefundStatusEnum;

/**
 * 支付、取消、超时和退款是否允许迁移。数据库条件更新与这里保持同一规则。
 */
public final class PaymentTransitionPolicy {
    public enum Capture {
        ALREADY,
        REFUND,
        CAPTURE
    }

    public enum Close {
        CLOSED,
        PAID,
        UNKNOWN
    }

    private PaymentTransitionPolicy() {
    }

    /** 回调到达后应入账、原路退回，还是视为已经处理。 */
    public static Capture decide(OrderStatusEnum status, PaymentStatusEnum payment) {
        if (payment == PaymentStatusEnum.PAID
                || payment == PaymentStatusEnum.PARTIAL_REFUND
                || payment == PaymentStatusEnum.FULL_REFUND
                || payment == PaymentStatusEnum.PAID_PENDING) {
            return Capture.ALREADY;
        }
        if (status == OrderStatusEnum.CANCELLED
                || payment == PaymentStatusEnum.TIMEOUT
                || payment == PaymentStatusEnum.CLOSED) {
            return Capture.REFUND;
        }
        return Capture.CAPTURE;
    }

    /** 只有未支付且未完成、未取消的订单可以关闭。 */
    public static boolean canCancel(OrderStatusEnum status, PaymentStatusEnum payment) {
        return payment == PaymentStatusEnum.UNPAID
                && status != OrderStatusEnum.COMPLETED
                && status != OrderStatusEnum.CANCELLED
                && payment != PaymentStatusEnum.RECONCILING;
    }

    /** 超时任务不能覆盖已收款、退款或对账中的订单。 */
    public static boolean canTimeout(OrderStatusEnum status, PaymentStatusEnum payment) {
        return payment == PaymentStatusEnum.UNPAID
                && status != OrderStatusEnum.CANCELLED
                && status != OrderStatusEnum.COMPLETED;
    }

    /** 退款单仍在申请或处理中时，渠道成功后才能提交业务。 */
    public static boolean refundAwaitingChannel(RefundStatusEnum status) {
        return status == RefundStatusEnum.REQUESTED || status == RefundStatusEnum.PROCESSING;
    }

    public static Close interpretWeChatTradeState(String state) {
        if (state == null) {
            return Close.UNKNOWN;
        }
        return switch (state) {
            case "SUCCESS", "REFUND" -> Close.PAID;
            case "CLOSED", "REVOKED", "NOTPAY", "PAYERROR" -> Close.CLOSED;
            default -> Close.UNKNOWN;
        };
    }

    public static Close interpretAlipayTrade(boolean querySuccess, String tradeStatus, String subCode) {
        if (!querySuccess && "ACQ.TRADE_NOT_EXIST".equals(subCode)) {
            return Close.CLOSED;
        }
        if (tradeStatus == null) {
            return Close.UNKNOWN;
        }
        return switch (tradeStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> Close.PAID;
            case "TRADE_CLOSED" -> Close.CLOSED;
            default -> Close.UNKNOWN;
        };
    }

    public static Close parseOutcome(String outcome) {
        if ("CLOSED".equals(outcome)) {
            return Close.CLOSED;
        }
        if ("PAID".equals(outcome)) {
            return Close.PAID;
        }
        return Close.UNKNOWN;
    }
}
