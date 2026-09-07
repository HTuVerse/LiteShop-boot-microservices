package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hongtu.restaurant.DTO.RestaurantOrderItem;
import org.hongtu.restaurant.DAO.RestaurantOrderItemMapper;
import org.hongtu.restaurant.service.RestaurantOrderItemService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单商品表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
public class RestaurantOrderItemServiceImpl extends ServiceImpl<RestaurantOrderItemMapper, RestaurantOrderItem> implements RestaurantOrderItemService {

    /**
     * 获取订单的最大批次号
     *
     * @param orderId 订单ID
     * @return 最大批次号，如果没有则返回0
     */
    @Override
    public Integer getMaxBatchNo(Long orderId) {
        LambdaQueryWrapper<RestaurantOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RestaurantOrderItem::getOrderId, orderId)
                .orderByDesc(RestaurantOrderItem::getBatchNo)
                .last("LIMIT 1");

        RestaurantOrderItem item = this.getOne(wrapper);
        return item != null && item.getBatchNo() != null ? item.getBatchNo() : 0;
    }
}
