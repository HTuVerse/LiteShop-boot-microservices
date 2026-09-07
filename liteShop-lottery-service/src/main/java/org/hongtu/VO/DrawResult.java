package org.hongtu.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hongtu.DTO.ActivityPrize;
import org.hongtu.DTO.LotteryPrize;

/**
 * 抽奖结果封装类
 *
 * @author Honghong
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DrawResult {

    /**
     * 是否中奖
     */
    private boolean won;

    /**
     * 中奖的奖品（未中奖时为null）
     */
    private LotteryPrize prize;

    /**
     * 中奖的活动奖品关联信息（未中奖时为null）
     */
    private ActivityPrize activityPrize;

    /**
     * 抽奖信息
     */
    private String message;

    /**
     * 创建未中奖结果
     *
     * @param message 消息
     * @return 未中奖结果
     */
    public static DrawResult notWon(String message) {
        return new DrawResult(false, null, null, message);
    }

    /**
     * 创建中奖结果
     *
     * @param prize         中奖奖品
     * @param activityPrize 活动奖品关联信息
     * @return 中奖结果
     */
    public static DrawResult won(LotteryPrize prize, ActivityPrize activityPrize) {
        return new DrawResult(true, prize, activityPrize, "恭喜中奖：" + prize.getName());
    }
}
