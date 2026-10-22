package org.hongtu.DAO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hongtu.DTO.CouponMessageReceipt;

import java.util.List;

/** 优惠券消息消费幂等凭据。 */
@Mapper
public interface CouponMessageReceiptMapper {

    @Insert("INSERT IGNORE INTO coupon_message_receipt(event_id, tenant_id, created_at) "
            + "VALUES(#{eventId}, #{tenantId}, NOW())")
    int claim(@Param("eventId") String eventId, @Param("tenantId") Long tenantId);

    /** 扫描本地已提交、抽奖服务尚未确认的发券事件。 */
    @Select("SELECT event_id AS eventId, tenant_id AS tenantId FROM coupon_message_receipt " +
            "WHERE event_id LIKE 'lottery-%' AND lottery_notified_at IS NULL " +
            "ORDER BY created_at LIMIT 100")
    List<CouponMessageReceipt> pendingLotteryNotices();

    /** 抽奖服务确认成功后独立提交通知状态。 */
    @Update("UPDATE coupon_message_receipt SET lottery_notified_at = NOW() " +
            "WHERE event_id = #{eventId} AND tenant_id = #{tenantId} AND lottery_notified_at IS NULL")
    int markLotteryNotified(@Param("eventId") String eventId, @Param("tenantId") Long tenantId);
}
