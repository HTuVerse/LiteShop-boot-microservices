package org.hongtu.restaurant.payment;

/**
 * 付款码（被扫）扣款结果。paid 为 true 时 transactionId 为渠道流水号。
 */
public record ScanPayOutcome(boolean paid, String transactionId, String message) {

    public static ScanPayOutcome paid(String transactionId) {
        return new ScanPayOutcome(true, transactionId, null);
    }

    public static ScanPayOutcome unpaid(String message) {
        return new ScanPayOutcome(false, null, message);
    }
}
