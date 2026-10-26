package org.hongtu.restaurant.payment;

/** 统一表示支付渠道的退款结果，以及余额退款的本地入账分支。 */
public enum RefundChannelState {
    SUCCESS, PROCESSING, FAILED, BALANCE;

    public static RefundChannelState fromProviderStatus(String status) {
        if ("SUCCESS".equals(status)) return SUCCESS;
        if ("PROCESSING".equals(status)) return PROCESSING;
        return FAILED;
    }
}
