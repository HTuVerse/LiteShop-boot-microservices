package org.hongtu.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.UserPointsRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 用户积分记录DTO Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-08-06
 */
@Mapper
public interface UserPointsRecordMapper extends BaseMapper<UserPointsRecord> {

}
