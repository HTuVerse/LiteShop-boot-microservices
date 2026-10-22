package org.hongtu.api.client;

import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** 支付服务验签后向餐厅订单服务提交可信支付结果。 */
@FeignClient(name = "liteShop-restaurant-service", contextId = "restaurantPaymentFeignClient")
public interface RestaurantPaymentFeignClient {
    /** 由餐厅服务校验订单归属、金额并完成支付状态流转。 */
    @PostMapping("/inner/restaurant/payment/paid")
    Result<Boolean> paid(@RequestParam("tenantId") Long tenantId,
                         @RequestBody PaymentNotifyResult notice);

    /** 由餐厅服务校验退款单并完成退款状态流转。 */
    @PostMapping("/inner/restaurant/payment/refunded")
    Result<Boolean> refunded(@RequestParam("tenantId") Long tenantId,
                             @RequestBody PaymentRefundNotifyResult notice);
}
