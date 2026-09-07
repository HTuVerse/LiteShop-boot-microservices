package org.hongtu.restaurant.service;

import org.hongtu.restaurant.DTO.RestaurantOrderItem;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 订单商品表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface RestaurantOrderItemService extends IService<RestaurantOrderItem> {

    /**
     * 获取订单的最大批次号
     * 用于追加菜品时确定新批次号
     *
     * @param orderId 订单ID
     * @return 最大批次号，如果没有则返回0
     */
    Integer getMaxBatchNo(Long orderId);
}
