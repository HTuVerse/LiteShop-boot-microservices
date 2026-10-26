package org.hongtu.restaurant.payment;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.PayFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DTO.OrderRefund;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** 隔离微信、支付宝退款查询与申请，保持渠道金额和状态转换一致。 */
@Service
@RequiredArgsConstructor
public class RefundChannelClient {
    private final PayFeignClient pay;

    public RefundChannelState request(RestaurantOrder order, OrderRefund refund) {
        if (order == null) return RefundChannelState.FAILED;
        boolean captured = order.getPaymentStatus() == PaymentStatusEnum.PAID
                || order.getPaymentStatus() == PaymentStatusEnum.PARTIAL_REFUND
                || order.getPaymentStatus() == PaymentStatusEnum.TIMEOUT
                || order.getPaymentStatus() == PaymentStatusEnum.CLOSED
                || order.getPaymentStatus() == PaymentStatusEnum.PAID_PENDING;
        PaymentTypeEnum type = refund.getChannel();
        if (!captured || type == PaymentTypeEnum.CASH || type == PaymentTypeEnum.BANK_CARD
                || type == PaymentTypeEnum.OFFLINE_PAY || type == PaymentTypeEnum.COUPON_PAY)
            return RefundChannelState.SUCCESS;
        if (type == PaymentTypeEnum.BANLANCE_PAY) return RefundChannelState.BALANCE;
        if (type == PaymentTypeEnum.WECHAT) return requestWeChat(order, refund);
        if (type == PaymentTypeEnum.ALIPAY) return requestAlipay(order, refund);
        throw new BusinessException("当前支付方式尚未接入退款，禁止修改本地支付状态");
    }

    private RefundChannelState requestWeChat(RestaurantOrder order, OrderRefund refund) {
        Map<String, Object> trade = FeignResultUtil.unwrap(pay.queryWeChatTrade(order.getTenantId(), order.getOrderNo()));
        if (!Boolean.TRUE.equals(trade.get("success")) || !"SUCCESS".equals(String.valueOf(trade.get("tradeState"))))
            throw new BusinessException("微信支付原交易状态异常，无法退款");
        Object total = trade.get("total");
        if (!(total instanceof Number totalNumber))
            throw new BusinessException("微信支付原交易金额缺失，无法退款");
        Map<String, Object> current = FeignResultUtil.unwrap(
                pay.queryWeChatRefund(order.getTenantId(), refund.getRefundNo()));
        if (!Boolean.TRUE.equals(current.get("success"))) {
            String cents = toCentString(refund.getAmount());
            current = FeignResultUtil.unwrap(pay.refundRestaurantWeChatTrade(order.getTenantId(),
                    order.getOrderNo(), refund.getRefundNo(), cents, String.valueOf(totalNumber.longValue()),
                    StrUtil.blankToDefault(refund.getReason(), "订单退菜退款")));
        }
        if (!Boolean.TRUE.equals(current.get("success"))) return RefundChannelState.FAILED;
        if (current.get("refundId") != null) refund.setChannelRefundId(String.valueOf(current.get("refundId")));
        return RefundChannelState.fromProviderStatus(String.valueOf(current.get("status")));
    }

    private RefundChannelState requestAlipay(RestaurantOrder order, OrderRefund refund) {
        Map<String, Object> current = FeignResultUtil.unwrap(pay.refundAlipayTrade(
                order.getTenantId(), order.getOrderNo(), refund.getRefundNo(),
                refund.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                StrUtil.blankToDefault(refund.getReason(), "订单退菜退款")));
        if (!Boolean.TRUE.equals(current.get("success"))) return RefundChannelState.FAILED;
        if (current.get("tradeNo") != null) refund.setChannelRefundId(String.valueOf(current.get("tradeNo")));
        return RefundChannelState.SUCCESS;
    }

    private String toCentString(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }
}
