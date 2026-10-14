package org.hongtu.api.client;

import org.hongtu.Result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 抽奖服务内部接口。
 */
@FeignClient(name = "liteShop-lottery-service", contextId = "lotteryFeignClient")
public interface LotteryFeignClient {

    /** 优惠券消费者发放成功后回写中奖记录。 */
    @PostMapping("/inner/lottery/prize-received")
    Result<Void> markPrizeReceived(@RequestParam("eventId") String eventId);
}
