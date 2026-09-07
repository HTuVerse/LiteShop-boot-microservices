package org.hongtu.api.client;

import org.hongtu.Result.Result;
import org.hongtu.api.dto.StoreSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 餐饮服务内部接口，用于跨库业务归属校验。 */
@FeignClient(name = "liteShop-restaurant-service", contextId = "restaurantFeignClient")
public interface RestaurantFeignClient {

    /** 校验门店 ID 是否全部属于当前租户。 */
    @GetMapping("/inner/restaurant/stores/validate")
    Result<Boolean> validateStoreIds(@RequestParam("storeIds") List<Long> storeIds);

    /** 查询当前租户内的门店名称；传入 ID 时保持授权列表边界。 */
    @GetMapping("/inner/restaurant/stores/options")
    Result<List<StoreSummaryDTO>> getStoreOptions(@RequestParam("storeIds") List<Long> storeIds);
}
