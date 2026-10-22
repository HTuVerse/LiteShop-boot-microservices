package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.UserExpRecord;

/** 会员经验流水的数据访问接口。 */
@Mapper
public interface UserExpRecordMapper extends BaseMapper<UserExpRecord> {}
