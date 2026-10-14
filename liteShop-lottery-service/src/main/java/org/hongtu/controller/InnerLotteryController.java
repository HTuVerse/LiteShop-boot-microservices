package org.hongtu.controller;

import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.service.LotteryPrizePublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抽奖服务内部接口。
 */
@RestController
@RequestMapping("/inner/lottery")
public class InnerLotteryController {

    @Resource
    private LotteryPrizePublisher lotteryPrizePublisher;

    /** 优惠券发放成功后，把对应中奖记录标为已领取。 */
    @PostMapping("/prize-received")
    public Result<Void> markPrizeReceived(@RequestParam String eventId) {
        lotteryPrizePublisher.markPrizeReceived(eventId);
        return Result.success();
    }
}
