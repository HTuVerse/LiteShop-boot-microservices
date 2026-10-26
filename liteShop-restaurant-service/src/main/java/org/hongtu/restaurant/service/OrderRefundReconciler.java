package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.DAO.OrderRefundMapper;
import org.hongtu.restaurant.DTO.OrderRefund;
import org.hongtu.restaurant.enums.RefundStatusEnum;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** 退款对账调度：选择到期退款、控制退避并标记需要人工核对的异常单。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRefundReconciler {
    private final OrderRefundMapper refunds;

    public void reconcile(Consumer<OrderRefund> processor) {
        LocalDateTime now = LocalDateTime.now();
        List<OrderRefund> open = TenantIgnore.get(() -> refunds.selectList(
                new LambdaQueryWrapper<OrderRefund>()
                        .in(OrderRefund::getStatus, RefundStatusEnum.REQUESTED, RefundStatusEnum.PROCESSING)
                        .eq(OrderRefund::getReviewRequired, false)
                        .le(OrderRefund::getNextRetryAt, now)
                        .lt(OrderRefund::getUpdatedAt, now.minusMinutes(1))
                        .orderByAsc(OrderRefund::getNextRetryAt)
                        .last("LIMIT 20")));
        for (OrderRefund refund : open) {
            try {
                processor.accept(refund);
            } catch (RuntimeException exception) {
                recordFailure(refund, exception);
            }
        }
    }

    private void recordFailure(OrderRefund refund, RuntimeException exception) {
        log.warn("退款补偿失败: refundNo={}", refund.getRefundNo(), exception);
        try {
            int previous = refund.getRetryCount() == null ? 0 : refund.getRetryCount();
            int attempts = previous + 1;
            boolean review = attempts >= 10;
            long delaySeconds = Math.min(3600L, 60L << Math.min(previous, 6));
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            if (reason.length() > 480) reason = reason.substring(0, 480);
            String lastError = reason;
            TenantScope.run(refund.getTenantId(), () -> refunds.recordRetry(
                    refund.getId(), previous, attempts, LocalDateTime.now().plusSeconds(delaySeconds),
                    review, lastError));
            if (review) log.error("退款自动对账已达重试上限，需人工核对: refundNo={}", refund.getRefundNo());
        } catch (RuntimeException failure) {
            log.error("退款补偿延期失败: refundNo={}", refund.getRefundNo(), failure);
        }
    }
}
