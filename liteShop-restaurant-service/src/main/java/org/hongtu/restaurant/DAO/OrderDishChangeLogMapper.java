package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.OrderDishChangeLog;

/**
 * 加退菜事件日志 Mapper。
 */
@Mapper
public interface OrderDishChangeLogMapper extends BaseMapper<OrderDishChangeLog> {
}
