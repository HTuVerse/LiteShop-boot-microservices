package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.hongtu.restaurant.DTO.LocalOutbox;

/** 餐厅本地发件箱。 */
@Mapper
public interface LocalOutboxMapper extends BaseMapper<LocalOutbox> {

    /** 跟随调用方事务提交，回滚时此标记也回滚。 */
    @Update("UPDATE local_outbox SET committed = 1 WHERE tenant_id = #{tenantId} AND event_id = #{eventId} AND status = 'SENT'")
    int markCommitted(@Param("tenantId") Long tenantId, @Param("eventId") String eventId);

    /** 没有明确提交或回滚结论的旧事件仅进入人工复核，不能擅自补偿。 */
    @Update("UPDATE local_outbox SET status = 'NEEDS_REVIEW', last_error = '事务结论未确认', updated_at = NOW() " +
            "WHERE tenant_id = #{tenantId} AND event_id = #{eventId} AND committed = 0 " +
            "AND status IN ('PENDING', 'SENT', 'UNKNOWN')")
    int markNeedsReviewIfUndecided(@Param("tenantId") Long tenantId, @Param("eventId") String eventId);
}
