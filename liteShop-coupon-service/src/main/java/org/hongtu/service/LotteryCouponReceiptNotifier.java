package org.hongtu.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponMessageReceiptMapper;
import org.hongtu.DTO.CouponMessageReceipt;
import org.hongtu.api.client.LotteryFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 发券事务提交后通知抽奖服务，失败由凭据表持续重试。 */
@Slf4j
@Service
public class LotteryCouponReceiptNotifier {
    @Resource private CouponMessageReceiptMapper receipts;
    @Resource private LotteryFeignClient lottery;
    private final TransactionTemplate independent;

    public LotteryCouponReceiptNotifier(PlatformTransactionManager manager) {
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 即时通知失败不影响已经提交的发券事务。 */
    public void notifyOne(String eventId) {
        try {
            FeignResultUtil.check(lottery.markPrizeReceived(eventId));
            Long tenantId = TenantContext.getTenantId();
            independent.executeWithoutResult(status -> receipts.markLotteryNotified(eventId, tenantId));
        } catch (RuntimeException exception) {
            log.error("抽奖发券已提交，领取通知等待重试: eventId={}", eventId, exception);
        }
    }

    /** 按租户恢复上下文，扫描全部待通知凭据。 */
    public void reconcile() {
        for (CouponMessageReceipt receipt : TenantIgnore.get(receipts::pendingLotteryNotices)) {
            if (receipt.getTenantId() != null) {
                TenantScope.run(receipt.getTenantId(), () -> notifyOne(receipt.getEventId()));
            }
        }
    }
}
