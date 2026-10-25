package org.hongtu.restaurant.service;

/**
 * 把跨服务写操作记入已提交的发件箱，避免远程成功后本地事务回滚却没有补偿记录。
 */
public interface LocalOutboxService {

    /**
     * 独立事务写入 PENDING，执行远程调用，成功后标为 SENT，外层事务提交后再标 DONE。
     * 外层回滚后记录会停在 SENT，由对账任务补偿。
     */
    void executeRemote(String eventType, String eventId, String payload, Runnable remoteCall);

    /** 与订单状态同事务记录会员奖励同步事件，提交后投递，失败由对账任务重试。 */
    void enqueueMemberReward(Long orderId, String eventId);

    /** 与门店删除同事务记录额度释放，提交后投递，失败自动重试。 */
    void enqueueStoreQuotaRelease(Long storeId);

    /** 扫描超时的 SENT/PENDING 记录并补偿或重试。 */
    void reconcile();
}
