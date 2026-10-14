package org.hongtu.ai.client;

import org.hongtu.api.client.RestaurantFeignClient;
import org.hongtu.api.dto.AiManagerDataRequest;
import org.hongtu.api.util.FeignResultUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 餐饮服务经营数据客户端。只读取已经按租户和门店裁剪过的指标，不连接业务库。
 */
@Component
public class AiBusinessDataClient {
    private final RestaurantFeignClient restaurantFeignClient;

    public AiBusinessDataClient(RestaurantFeignClient restaurantFeignClient) {
        this.restaurantFeignClient = restaurantFeignClient;
    }

    /** 查询当前账号可选的租户或门店范围。 */
    public Map<String, Object> scope() {
        return FeignResultUtil.unwrap(restaurantFeignClient.getAiManagerScope());
    }

    /** 查询指定范围和数据模式的经营指标。 */
    public Map<String, Object> context(AiManagerDataRequest request) {
        return FeignResultUtil.unwrap(restaurantFeignClient.getAiManagerContext(request));
    }
}
