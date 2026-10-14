package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.StoreMenuPolicy;

/**
 * 门店菜单策略数据访问接口。
 */
@Mapper
public interface StoreMenuPolicyMapper extends BaseMapper<StoreMenuPolicy> {
}
