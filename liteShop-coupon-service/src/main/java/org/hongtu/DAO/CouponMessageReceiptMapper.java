package org.hongtu.DAO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 优惠券消息消费幂等凭据。 */
@Mapper
public interface CouponMessageReceiptMapper {

    @Insert("INSERT IGNORE INTO coupon_message_receipt(event_id, tenant_id, created_at) "
            + "VALUES(#{eventId}, #{tenantId}, NOW())")
    int claim(@Param("eventId") String eventId, @Param("tenantId") Long tenantId);
}
