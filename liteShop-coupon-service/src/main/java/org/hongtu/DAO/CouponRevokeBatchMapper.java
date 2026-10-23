package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hongtu.DTO.CouponRevokeBatch;

import java.util.List;

/** 模板回收任务持久化接口。 */
@Mapper
public interface CouponRevokeBatchMapper extends BaseMapper<CouponRevokeBatch> {
    /** 停用租户的未完成任务也必须继续执行。 */
    @Select("SELECT DISTINCT tenant_id FROM coupon_revoke_batch WHERE status = 0")
    List<Long> pendingTenantIds();

    /** 失败任务后移，避免阻塞同租户后续批次。 */
    @Update("UPDATE coupon_revoke_batch SET updated_at = NOW() WHERE batch_id = #{batchId} AND status = 0")
    int deferPending(@Param("batchId") String batchId);
}
