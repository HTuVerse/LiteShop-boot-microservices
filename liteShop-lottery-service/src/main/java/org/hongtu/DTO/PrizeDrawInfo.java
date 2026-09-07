package org.hongtu.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 抽奖信息封装类
 * 用于在抽奖算法中传递奖品和概率信息
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrizeDrawInfo {

    /**
     * 奖品信息
     */
    private LotteryPrize prize;

    /**
     * 活动奖品关联信息（包含库存）
     */
    private ActivityPrize activityPrize;

    /**
     * 原始概率（配置的概率值）
     */
    private BigDecimal originalProbability;

    /**
     * 归一化后的概率（用于实际抽奖计算）
     */
    private BigDecimal normalizedProbability;
}
