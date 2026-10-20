package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.CouponRevokeRecord;

/** 用户券回收审计记录持久化接口。 */
@Mapper
public interface CouponRevokeRecordMapper extends BaseMapper<CouponRevokeRecord> {
}
