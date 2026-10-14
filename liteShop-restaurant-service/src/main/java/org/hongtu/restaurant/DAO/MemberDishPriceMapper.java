package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.MemberDishPrice;

/**
 * 会员菜品价数据访问接口。
 */
@Mapper
public interface MemberDishPriceMapper extends BaseMapper<MemberDishPrice> {
}
