package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.hongtu.restaurant.DTO.OrderRefund;

/**
 * 订单退款单。状态迁移只允许从已申请或处理中向前。
 */
@Mapper
public interface OrderRefundMapper extends BaseMapper<OrderRefund> {

    @Update("""
            UPDATE order_refund
            SET status = #{status}, channel_refund_id = #{channelRefundId}, updated_at = NOW()
            WHERE id = #{id} AND status IN (0, 1)
            """)
    int compareAndSetOpen(@Param("id") Long id, @Param("status") Integer status,
                          @Param("channelRefundId") String channelRefundId);
}
