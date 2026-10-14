package org.hongtu;

import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.hongtu.restaurant.utils.CashierAmountGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 收银金额防篡改测试。
 */
class CashierAmountGuardTest {

    @Test
    void rejectsTamperedZeroOrderTotal() {
        assertThrows(BusinessException.class, () -> CashierAmountGuard.requireClientAmountMatches(
                BigDecimal.ZERO, new BigDecimal("88.00")));
    }

    @Test
    void paymentUsesDiscountedServerPayableInsteadOfOriginalTotal() {
        assertEquals(new BigDecimal("68.00"), CashierAmountGuard.requireServerPayable(
                new BigDecimal("68.00"), new BigDecimal("88.00")));
    }

    @Test
    void rejectsPaymentNotificationAmountMismatch() {
        assertThrows(BusinessException.class, () -> CashierAmountGuard.requireClientAmountMatches(
                new BigDecimal("0.01"), new BigDecimal("68.00")));
    }

    @Test
    void rejectsZeroCashTenderWhenServerPayableIsPositive() {
        assertThrows(BusinessException.class, () -> CashierAmountGuard.requireTenderedAmount(
                PaymentTypeEnum.CASH, BigDecimal.ZERO, new BigDecimal("88.00")));
    }

    @Test
    void cashTenderMayExceedPayableButDoesNotChangePayable() {
        BigDecimal tendered = CashierAmountGuard.requireTenderedAmount(
                PaymentTypeEnum.CASH, new BigDecimal("100.00"), new BigDecimal("88.00"));

        assertEquals(new BigDecimal("100.00"), tendered);
    }

    @Test
    void nonCashPaymentMustExactlyMatchServerPayable() {
        assertThrows(BusinessException.class, () -> CashierAmountGuard.requireTenderedAmount(
                PaymentTypeEnum.BANK_CARD, new BigDecimal("0.01"), new BigDecimal("88.00")));
    }

    @Test
    void rejectsZeroDiscountRateAndZeroAfterRounding() {
        assertThrows(BusinessException.class, () -> CashierAmountGuard.applyManualDiscount(
                new BigDecimal("88.00"), BigDecimal.ZERO));
        assertThrows(BusinessException.class, () -> CashierAmountGuard.applyManualDiscount(
                new BigDecimal("0.01"), new BigDecimal("0.01")));
    }

    @Test
    void rejectsUnsupportedSettlementPaymentType() {
        assertThrows(BusinessException.class, () -> CashierAmountGuard.requireSettlementPaymentType(
                PaymentTypeEnum.WECHAT));
    }
}
