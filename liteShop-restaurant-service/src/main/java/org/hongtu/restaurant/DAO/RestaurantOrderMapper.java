package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hongtu.restaurant.DTO.OrderTenantRef;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 餐厅订单表 Mapper 接口。支付状态迁移使用条件更新。
 */
@Mapper
public interface RestaurantOrderMapper extends BaseMapper<RestaurantOrder> {

    /** 回调只按全局唯一订单号读取主键和租户。 */
    @Select("SELECT id, tenant_id AS tenantId FROM restaurant_order WHERE order_no = #{orderNo} LIMIT 1")
    OrderTenantRef findTenantByOrderNo(@Param("orderNo") String orderNo);

    /** 未支付、支付失败或对账中，且订单未取消时标记已支付。 */
    @Update("""
            UPDATE restaurant_order
            SET payment_status = 1,
                payment_type = #{paymentType},
                payment_no = #{paymentNo},
                payment_time = #{paymentTime},
                actual_amount = #{actualAmount},
                sales_committed = 1,
                stock_reserved = 1,
                updated_at = NOW()
            WHERE id = #{id}
              AND payment_status IN (0, 6, 7)
              AND status <> 6
            """)
    int markPaidIfCapturable(@Param("id") Long id, @Param("paymentType") Integer paymentType,
                             @Param("paymentNo") String paymentNo, @Param("paymentTime") LocalDateTime paymentTime,
                             @Param("actualAmount") BigDecimal actualAmount);

    /** 资金已到账但库存不足时保持已收款，不能退回未支付。 */
    @Update("""
            UPDATE restaurant_order
            SET payment_status = 8,
                payment_type = #{paymentType},
                payment_no = #{paymentNo},
                payment_time = #{paymentTime},
                actual_amount = #{actualAmount},
                updated_at = NOW()
            WHERE id = #{id}
              AND payment_status IN (0, 6, 7)
              AND status <> 6
            """)
    int markPaidPendingIfCapturable(@Param("id") Long id, @Param("paymentType") Integer paymentType,
                                    @Param("paymentNo") String paymentNo, @Param("paymentTime") LocalDateTime paymentTime,
                                    @Param("actualAmount") BigDecimal actualAmount);

    /** 关单确认后取消未支付订单。 */
    @Update("""
            UPDATE restaurant_order
            SET status = 6,
                payment_status = 5,
                cancel_reason = #{reason},
                cancel_time = #{cancelTime},
                stock_reserved = 0,
                sales_committed = 0,
                updated_at = NOW()
            WHERE id = #{id}
              AND payment_status = 0
              AND status <> 5
              AND status <> 6
            """)
    int markClosedIfUnpaid(@Param("id") Long id, @Param("reason") String reason,
                           @Param("cancelTime") LocalDateTime cancelTime);

    /** 超时确认后取消未支付订单。 */
    @Update("""
            UPDATE restaurant_order
            SET status = 6,
                payment_status = 4,
                cancel_reason = #{reason},
                cancel_time = #{cancelTime},
                stock_reserved = 0,
                sales_committed = 0,
                updated_at = NOW()
            WHERE id = #{id}
              AND payment_status = 0
              AND status <> 5
              AND status <> 6
            """)
    int markTimeoutIfUnpaid(@Param("id") Long id, @Param("reason") String reason,
                            @Param("cancelTime") LocalDateTime cancelTime);

    /** 关单结果不明确时进入对账，不释放库存。 */
    @Update("""
            UPDATE restaurant_order
            SET payment_status = 7, updated_at = NOW()
            WHERE id = #{id}
              AND payment_status = 0
              AND status <> 5
              AND status <> 6
            """)
    int markReconcilingIfUnpaid(@Param("id") Long id);

    /** 渠道退款成功后更新部分或全额退款。 */
    @Update("""
            UPDATE restaurant_order
            SET payment_status = #{paymentStatus},
                stock_reserved = #{stockReserved},
                sales_committed = #{salesCommitted},
                status = #{orderStatus},
                cancel_reason = #{cancelReason},
                cancel_time = #{cancelTime},
                updated_at = NOW()
            WHERE id = #{id}
              AND payment_status IN (1, 2, 4, 5, 8)
            """)
    int markRefunded(@Param("id") Long id, @Param("paymentStatus") Integer paymentStatus,
                     @Param("stockReserved") Integer stockReserved, @Param("salesCommitted") Integer salesCommitted,
                     @Param("orderStatus") Integer orderStatus, @Param("cancelReason") String cancelReason,
                     @Param("cancelTime") LocalDateTime cancelTime);
}
