package org.hongtu.DAO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 余额扣款与取消待定支付共用的订单号互斥栅栏。 */
@Mapper
public interface BalancePaymentFenceMapper {
    @Insert("INSERT INTO balance_payment_fence (tenant_id, order_no, status) " +
            "VALUES (#{tenantId}, #{orderNo}, 0) ON DUPLICATE KEY UPDATE order_no = order_no")
    int ensure(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);

    @Select("SELECT status FROM balance_payment_fence WHERE tenant_id = #{tenantId} " +
            "AND order_no = #{orderNo} FOR UPDATE")
    Integer lockStatus(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);

    @Update("UPDATE balance_payment_fence SET status = 1, updated_at = NOW() " +
            "WHERE tenant_id = #{tenantId} AND order_no = #{orderNo} AND status = 0")
    int abort(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);
}
