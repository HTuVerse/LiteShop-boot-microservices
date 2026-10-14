package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.StoreGroupMember;

/**
 * 门店分组成员数据访问接口。
 */
@Mapper
public interface StoreGroupMemberMapper extends BaseMapper<StoreGroupMember> {
}
