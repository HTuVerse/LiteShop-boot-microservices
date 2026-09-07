package org.hongtu.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.UserSurplusRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 余额变动历史表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-08-25
 */
@Mapper
public interface SurplusRecordMapper extends BaseMapper<UserSurplusRecord> {

}
