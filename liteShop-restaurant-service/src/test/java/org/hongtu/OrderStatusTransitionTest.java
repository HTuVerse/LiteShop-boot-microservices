package org.hongtu;

import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentModeEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.service.impl.RestaurantOrderServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 订单履约状态流转规则测试。
 */
class OrderStatusTransitionTest {

    @Test
    void paymentStatesAreNotOrderStates() {
        assertNull(OrderStatusEnum.getByCode(0));
        assertEquals(OrderStatusEnum.PLACED, OrderStatusEnum.getByCode(1));
        assertEquals(OrderStatusEnum.CANCELLED, OrderStatusEnum.getByCode(6));
    }

    @Test
    void onlyAllowsSequentialForwardTransitionsOrCancellation() {
        assertTrue(OrderStatusEnum.PLACED.canTransitionTo(OrderStatusEnum.ACCEPTED));
        assertTrue(OrderStatusEnum.ACCEPTED.canTransitionTo(OrderStatusEnum.PREPARING));
        assertTrue(OrderStatusEnum.PREPARING.canTransitionTo(OrderStatusEnum.PREPARED));
        assertTrue(OrderStatusEnum.PREPARED.canTransitionTo(OrderStatusEnum.COMPLETED));
        assertTrue(OrderStatusEnum.PREPARING.canTransitionTo(OrderStatusEnum.CANCELLED));

        assertFalse(OrderStatusEnum.PLACED.canTransitionTo(OrderStatusEnum.PREPARING));
        assertFalse(OrderStatusEnum.PREPARED.canTransitionTo(OrderStatusEnum.ACCEPTED));
        assertFalse(OrderStatusEnum.COMPLETED.canTransitionTo(OrderStatusEnum.CANCELLED));
        assertFalse(OrderStatusEnum.CANCELLED.canTransitionTo(OrderStatusEnum.PLACED));
    }

    @Test
    void serviceRejectsSkippingCancellationAndUnpaidOnlineFulfilment() {
        var service = spy(new RestaurantOrderServiceImpl());
        var order = new RestaurantOrder()
                .setId(1L)
                .setStatus(OrderStatusEnum.PLACED)
                .setPaymentMode(PaymentModeEnum.ONLINE)
                .setPaymentStatus(PaymentStatusEnum.UNPAID);
        doReturn(order).when(service).getById(1L);
        // 订单状态流转会先对订单行加锁（getOrderForUpdate 走 getOne(FOR UPDATE)）
        doReturn(order).when(service).getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doReturn(true).when(service).updateById(any(RestaurantOrder.class));

        assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(1L, OrderStatusEnum.PREPARING));
        assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(1L, OrderStatusEnum.CANCELLED));
        assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(1L, OrderStatusEnum.ACCEPTED));

        order.setPaymentStatus(PaymentStatusEnum.PAID);
        assertTrue(service.updateOrderStatus(1L, OrderStatusEnum.ACCEPTED));
        assertEquals(OrderStatusEnum.ACCEPTED, order.getStatus());
    }
}
