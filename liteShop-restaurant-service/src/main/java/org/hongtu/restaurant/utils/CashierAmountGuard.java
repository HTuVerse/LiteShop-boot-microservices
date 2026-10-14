package org.hongtu.restaurant.utils;

import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 收银金额安全校验，确保订单与支付只使用服务端计算结果。
 */
public final class CashierAmountGuard {

    private CashierAmountGuard() {
    }

    /**
     * 校验客户端展示金额与服务端计价结果一致。
     */
    public static void requireClientAmountMatches(BigDecimal clientAmount, BigDecimal serverAmount) {
        BigDecimal trustedAmount = requireTrustedAmount(serverAmount);
        if (clientAmount == null || clientAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("订单金额不正确");
        }
        BigDecimal submittedAmount = money(clientAmount);
        if (submittedAmount.compareTo(trustedAmount) != 0) {
            throw new BusinessException(String.format(
                    "订单金额验证失败，客户端提交：%s，服务端计算：%s",
                    submittedAmount, trustedAmount));
        }
    }

    /**
     * 获取订单的服务端应付金额，优惠后金额优先于原价总额。
     */
    public static BigDecimal requireServerPayable(BigDecimal actualAmount, BigDecimal totalAmount) {
        return requireTrustedAmount(actualAmount != null ? actualAmount : totalAmount);
    }

    /**
     * 按服务端应付金额计算人工折扣，禁止非法折扣率和正金额被折为零。
     */
    public static BigDecimal applyManualDiscount(BigDecimal serverPayable, BigDecimal discountRate) {
        BigDecimal trustedPayable = requireTrustedAmount(serverPayable);
        BigDecimal rate = discountRate == null ? BigDecimal.ONE : discountRate;
        if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("折扣率必须大于0且不超过1");
        }
        BigDecimal discounted = money(trustedPayable.multiply(rate));
        if (trustedPayable.compareTo(BigDecimal.ZERO) > 0 && discounted.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("折扣后应付金额不能为0");
        }
        return discounted;
    }

    /**
     * 校验桌位结算支付方式，只允许后台已实现并可核验的支付类型。
     */
    public static PaymentTypeEnum requireSettlementPaymentType(PaymentTypeEnum paymentType) {
        if (paymentType != PaymentTypeEnum.CASH
                && paymentType != PaymentTypeEnum.BANK_CARD
                && paymentType != PaymentTypeEnum.BANLANCE_PAY) {
            throw new BusinessException("桌位结算仅支持现金、银行卡或余额支付");
        }
        return paymentType;
    }

    /**
     * 校验客户端实收金额。现金可多收用于找零，其余支付方式必须与服务端应付一致。
     */
    public static BigDecimal requireTenderedAmount(PaymentTypeEnum paymentType,
                                                    BigDecimal clientTenderedAmount,
                                                    BigDecimal serverPayable) {
        BigDecimal trustedPayable = requireTrustedAmount(serverPayable);
        if (clientTenderedAmount == null) {
            return trustedPayable;
        }
        if (clientTenderedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("实收金额不正确");
        }
        BigDecimal tendered = money(clientTenderedAmount);
        if (paymentType == PaymentTypeEnum.CASH) {
            if (tendered.compareTo(trustedPayable) < 0) {
                throw new BusinessException(String.format(
                        "实收金额不足，应收：%s，实收：%s", trustedPayable, tendered));
            }
        } else if (tendered.compareTo(trustedPayable) != 0) {
            throw new BusinessException(String.format(
                    "支付金额不一致，应付：%s，客户端提交：%s", trustedPayable, tendered));
        }
        return tendered;
    }

    private static BigDecimal requireTrustedAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("服务端订单金额异常，请暂停结算并检查订单");
        }
        return money(amount);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
