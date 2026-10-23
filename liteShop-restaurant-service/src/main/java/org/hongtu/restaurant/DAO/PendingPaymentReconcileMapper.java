package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.restaurant.DTO.OrderTenantRef;

import java.util.List;

/** 跨租户分批定位待确认的线上渠道交易。 */
@Mapper
public interface PendingPaymentReconcileMapper {

    /** 按主键游标遍历未确认的渠道订单，失败响应也可能发生在渠道扣款之后。 */
    @Select("""
            SELECT id, tenant_id AS tenantId
            FROM restaurant_order
            WHERE id > #{afterId}
              AND payment_mode = 1
              AND ((payment_status IN (0, 6, 7, 9) AND payment_type IN (1, 2))
                OR (payment_status = 9 AND payment_type = 6
                    AND updated_at < DATE_SUB(NOW(), INTERVAL 1 MINUTE)))
              AND status NOT IN (5, 6)
            ORDER BY id
            LIMIT #{limit}
            """)
    List<OrderTenantRef> nextPending(@Param("afterId") long afterId, @Param("limit") int limit);
}
