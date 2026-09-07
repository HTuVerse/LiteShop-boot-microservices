package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.RestaurantOrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 订单商品表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Mapper
public interface RestaurantOrderItemMapper extends BaseMapper<RestaurantOrderItem> {

}
