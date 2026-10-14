package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.OrderPriceAdjustment;

/**
 * 订单价格调整快照数据访问接口。
 */
@Mapper
public interface OrderPriceAdjustmentMapper extends BaseMapper<OrderPriceAdjustment> {
}
