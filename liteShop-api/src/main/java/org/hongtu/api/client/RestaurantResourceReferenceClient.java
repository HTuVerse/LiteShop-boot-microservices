package org.hongtu.api.client;

import org.hongtu.Result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 文件服务查询餐饮域中是否仍有业务引用。 */
@FeignClient(name = "liteShop-restaurant-service", contextId = "restaurantResourceReferenceClient")
public interface RestaurantResourceReferenceClient {
    @GetMapping("/inner/resource-reference") Result<Boolean> used(@RequestParam("url") String url);
}
