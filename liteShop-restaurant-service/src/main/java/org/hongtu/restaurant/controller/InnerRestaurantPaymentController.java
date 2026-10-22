package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 接收支付服务已验签的通知，由餐厅服务处理订单状态。 */
@RestController
@RequestMapping("/inner/restaurant/payment")
@SaIgnore
@RequiredArgsConstructor
public class InnerRestaurantPaymentController {
    private final RestaurantOrderService restaurantOrderService;

    /** 校验支付通知中的租户、订单与金额后入账。 */
    @PostMapping("/paid")
    public Result<Boolean> paid(@RequestParam("tenantId") Long tenantId,
                                @RequestBody PaymentNotifyResult notice) {
        restaurantOrderService.applyVerifiedPaymentNotify(tenantId, notice);
        return Result.success(true);
    }

    /** 校验退款通知中的退款单、原订单与金额后入账。 */
    @PostMapping("/refunded")
    public Result<Boolean> refunded(@RequestParam("tenantId") Long tenantId,
                                    @RequestBody PaymentRefundNotifyResult notice) {
        restaurantOrderService.applyVerifiedRefundNotify(tenantId, notice);
        return Result.success(true);
    }
}
