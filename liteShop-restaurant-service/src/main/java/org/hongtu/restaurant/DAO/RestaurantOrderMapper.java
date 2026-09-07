package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 餐厅订单表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Mapper
public interface RestaurantOrderMapper extends BaseMapper<RestaurantOrder> {

}
