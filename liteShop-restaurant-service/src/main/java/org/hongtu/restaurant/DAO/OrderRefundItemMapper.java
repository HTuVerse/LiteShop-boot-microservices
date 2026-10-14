package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.OrderRefundItem;

/**
 * 退款明细。
 */
@Mapper
public interface OrderRefundItemMapper extends BaseMapper<OrderRefundItem> {
}
