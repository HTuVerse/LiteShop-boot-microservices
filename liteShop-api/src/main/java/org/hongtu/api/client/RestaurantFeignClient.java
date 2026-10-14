package org.hongtu.api.client;

import org.hongtu.Result.Result;
import org.hongtu.api.dto.AiManagerDataRequest;
import org.hongtu.api.dto.StoreSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** 餐饮服务内部接口，用于跨库业务归属校验。 */
@FeignClient(name = "liteShop-restaurant-service", contextId = "restaurantFeignClient")
public interface RestaurantFeignClient {

    /** 查询当前租户有效门店数量，供 SaaS 实时用量展示和校验。 */
    @GetMapping("/inner/restaurant/stores/count")
    Result<Long> countStores();

    /** 校验门店 ID 是否全部属于当前租户。 */
    @GetMapping("/inner/restaurant/stores/validate")
    Result<Boolean> validateStoreIds(@RequestParam("storeIds") List<Long> storeIds);

    /** 查询当前租户内的门店名称；传入 ID 时保持授权列表边界。 */
    @GetMapping("/inner/restaurant/stores/options")
    Result<List<StoreSummaryDTO>> getStoreOptions(@RequestParam("storeIds") List<Long> storeIds);

    /** 查询当前账号可使用的 AI 店长经营范围。 */
    @GetMapping("/inner/restaurant/ai-manager/scope")
    Result<Map<String, Object>> getAiManagerScope();

    /** 查询经过租户和门店权限校验的 AI 店长经营数据。 */
    @PostMapping("/inner/restaurant/ai-manager/context")
    Result<Map<String, Object>> getAiManagerContext(@RequestBody AiManagerDataRequest request);
}
