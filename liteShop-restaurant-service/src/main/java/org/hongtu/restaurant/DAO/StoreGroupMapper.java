package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.StoreGroup;

/**
 * 门店分组数据访问接口。
 */
@Mapper
public interface StoreGroupMapper extends BaseMapper<StoreGroup> {
}
