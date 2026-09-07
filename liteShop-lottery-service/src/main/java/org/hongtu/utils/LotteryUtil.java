package org.hongtu.utils;

import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.ActivityPrize;
import org.hongtu.DTO.LotteryPrize;
import org.hongtu.DTO.PrizeDrawInfo;
import org.hongtu.VO.DrawResult;
import org.hongtu.enums.BooleanEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抽奖工具类，提供基于概率和库存的抽奖算法
 * <p>
 * 核心算法说明：
 * 1. 概率归一化：当奖品概率之和超过1时，按比重重新分配概率
 *    例如：奖品A(0.8), B(0.6), C(0.3), D(0.9)，总和=2.6
 *    归一化后：A=0.8/2.6≈0.308, B=0.6/2.6≈0.231, C=0.3/2.6≈0.115, D=0.9/2.6≈0.346
 * 2. 库存过滤：只有库存大于0的奖品才参与抽奖
 * 3. 动态概率：库存变化时自动重新计算归一化概率
 *
 * @author Honghong
 */
@Slf4j
public class LotteryUtil {

    private static final SecureRandom random = new SecureRandom();

    /**
     * 概率计算精度（小数位数）
     */
    private static final int PROBABILITY_SCALE = 8;

    /**
     * 【核心方法】基于概率和库存进行抽奖
     * <p>
     * 算法流程：
     * 1. 过滤出有效奖品（启用、概率>0、库存>0）
     * 2. 计算所有有效奖品的概率总和
     * 3. 按比重归一化每个奖品的概率（确保总和为1）
     * 4. 生成随机数，根据归一化概率区间确定中奖奖品
     *
     * @param prizes         奖品列表
     * @param activityPrizes 活动奖品关联列表（包含库存信息）
     * @return 抽奖结果
     */
    public static DrawResult drawWithProbabilityAndStock(List<LotteryPrize> prizes,
                                                         List<ActivityPrize> activityPrizes) {
        if (prizes == null || prizes.isEmpty()) {
            return DrawResult.notWon("奖品列表为空");
        }

        if (activityPrizes == null || activityPrizes.isEmpty()) {
            return DrawResult.notWon("活动奖品关联列表为空");
        }

        // 构建奖品ID到ActivityPrize的映射
        Map<Long, ActivityPrize> activityPrizeMap = activityPrizes.stream()
                .collect(Collectors.toMap(ActivityPrize::getPrizeId, ap -> ap, (a, b) -> a));

        // 过滤出有效的奖品（启用、概率>0、库存>0）
        List<PrizeDrawInfo> validPrizeInfos = new ArrayList<>();
        for (LotteryPrize prize : prizes) {
            // 检查奖品是否启用且概率大于0
            if (prize.getEnabled() != BooleanEnum.YES
                    || prize.getProbability() == null
                    || prize.getProbability().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 检查库存
            ActivityPrize activityPrize = activityPrizeMap.get(prize.getId());
            if (activityPrize == null || activityPrize.getSurplusStock() == null
                    || activityPrize.getSurplusStock() <= 0) {
                log.debug("奖品[{}]库存不足，跳过", prize.getName());
                continue;
            }

            validPrizeInfos.add(new PrizeDrawInfo(
                    prize,
                    activityPrize,
                    prize.getProbability(),
                    null  // 归一化概率稍后计算
            ));
        }

        if (validPrizeInfos.isEmpty()) {
            return DrawResult.notWon("没有可用的奖品（库存不足或未启用）");
        }

        // 计算概率总和
        BigDecimal totalProbability = validPrizeInfos.stream()
                .map(PrizeDrawInfo::getOriginalProbability)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalProbability.compareTo(BigDecimal.ZERO) <= 0) {
            return DrawResult.notWon("总概率为0");
        }

        // 归一化概率：每个奖品的概率 = 原始概率 / 总概率
        for (PrizeDrawInfo info : validPrizeInfos) {
            BigDecimal normalized = info.getOriginalProbability()
                    .divide(totalProbability, PROBABILITY_SCALE, RoundingMode.HALF_UP);
            info.setNormalizedProbability(normalized);
        }

        // 打印归一化后的概率分布（使用INFO级别，便于调试）
        log.info("=== 抽奖概率分布 ===");
        log.info("有效奖品数量: {}, 原始概率总和: {}", validPrizeInfos.size(), totalProbability);
        for (PrizeDrawInfo info : validPrizeInfos) {
            BigDecimal percentage = info.getNormalizedProbability()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            log.info("  奖品[{}](ID={}): 原始概率={}, 归一化概率={}%, 库存={}",
                    info.getPrize().getName(),
                    info.getPrize().getId(),
                    info.getOriginalProbability(),
                    percentage,
                    info.getActivityPrize().getSurplusStock());
        }

        // 生成0-1之间的随机数
        double randomDouble = random.nextDouble();
        BigDecimal randomValue = BigDecimal.valueOf(randomDouble)
                .setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);

        log.info("生成随机数: {} (原始值: {})", randomValue, randomDouble);

        // 根据归一化概率区间判断中奖
        BigDecimal cumulativeProbability = BigDecimal.ZERO;
        for (PrizeDrawInfo info : validPrizeInfos) {
            BigDecimal previousCumulative = cumulativeProbability;
            cumulativeProbability = cumulativeProbability.add(info.getNormalizedProbability());

            log.info("  检查奖品[{}]: 区间=[{}, {}], 随机数={}",
                    info.getPrize().getName(),
                    previousCumulative,
                    cumulativeProbability,
                    randomValue);

            if (randomValue.compareTo(cumulativeProbability) <= 0) {
                log.info(">>> 抽奖结果：中奖奖品=[{}], 随机数={}, 落在区间=[{}, {}]",
                        info.getPrize().getName(), randomValue, previousCumulative, cumulativeProbability);
                return DrawResult.won(info.getPrize(), info.getActivityPrize());
            }
        }

        // 由于浮点数精度问题，返回最后一个有效奖品
        PrizeDrawInfo lastInfo = validPrizeInfos.get(validPrizeInfos.size() - 1);
        log.info(">>> 浮点精度兜底：中奖奖品=[{}]", lastInfo.getPrize().getName());
        return DrawResult.won(lastInfo.getPrize(), lastInfo.getActivityPrize());
    }

    /**
     * 带权重的抽奖算法（同时考虑库存）
     *
     * @param prizes             奖品列表
     * @param activityPrizes     活动奖品关联列表（包含库存信息）
     * @param userLevel          用户等级（1-10）
     * @param participationCount 参与次数（>=1）
     * @return 抽奖结果
     */
    public static DrawResult drawWithWeightAndStock(List<LotteryPrize> prizes,
                                                    List<ActivityPrize> activityPrizes,
                                                    int userLevel,
                                                    int participationCount) {
        if (prizes == null || prizes.isEmpty()) {
            return DrawResult.notWon("奖品列表为空");
        }

        // 参数边界处理
        userLevel = Math.max(1, Math.min(userLevel, 10));
        participationCount = Math.max(1, participationCount);

        // 计算用户权重因子 (0.8-1.2之间)
        double userFactor = 0.8 + (userLevel * 0.04);
        userFactor = Math.min(userFactor, 1.2);

        // 计算参与次数权重因子 (首次参与有更高概率)
        double participationFactor = participationCount == 1 ? 1.1 : (1.0 - participationCount * 0.01);
        participationFactor = Math.max(participationFactor, 0.7);

        // 应用权重到每个奖品概率
        List<LotteryPrize> weightedPrizes = new ArrayList<>();
        for (LotteryPrize prize : prizes) {
            if (prize.getEnabled() != BooleanEnum.YES || prize.getProbability() == null) {
                continue;
            }

            LotteryPrize weightedPrize = new LotteryPrize();
            weightedPrize.setId(prize.getId());
            weightedPrize.setName(prize.getName());
            weightedPrize.setImgUrl(prize.getImgUrl());
            weightedPrize.setType(prize.getType());
            weightedPrize.setCouponId(prize.getCouponId());
            weightedPrize.setPoints(prize.getPoints());
            weightedPrize.setEnabled(prize.getEnabled());

            BigDecimal adjustedProbability = prize.getProbability()
                    .multiply(BigDecimal.valueOf(userFactor))
                    .multiply(BigDecimal.valueOf(participationFactor))
                    .setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);

            weightedPrize.setProbability(adjustedProbability);
            weightedPrizes.add(weightedPrize);
        }

        // 使用带库存的抽奖算法
        return drawWithProbabilityAndStock(weightedPrizes, activityPrizes);
    }

    /**
     * 计算并打印概率分布信息（用于调试和验证）
     *
     * @param prizes         奖品列表
     * @param activityPrizes 活动奖品关联列表
     */
    public static void printProbabilityDistribution(List<LotteryPrize> prizes,
                                                    List<ActivityPrize> activityPrizes) {
        if (prizes == null || prizes.isEmpty()) {
            log.info("奖品列表为空");
            return;
        }

        Map<Long, ActivityPrize> activityPrizeMap = activityPrizes != null
                ? activityPrizes.stream().collect(Collectors.toMap(ActivityPrize::getPrizeId, ap -> ap, (a, b) -> a))
                : Map.of();

        // 计算有效奖品的总概率
        BigDecimal totalProbability = BigDecimal.ZERO;
        List<LotteryPrize> validPrizes = new ArrayList<>();

        for (LotteryPrize prize : prizes) {
            if (prize.getEnabled() != BooleanEnum.YES
                    || prize.getProbability() == null
                    || prize.getProbability().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            ActivityPrize ap = activityPrizeMap.get(prize.getId());
            if (ap != null && ap.getSurplusStock() != null && ap.getSurplusStock() <= 0) {
                continue;
            }

            validPrizes.add(prize);
            totalProbability = totalProbability.add(prize.getProbability());
        }

        log.info("========== 概率分布信息 ==========");
        log.info("有效奖品数量: {}", validPrizes.size());
        log.info("原始概率总和: {}", totalProbability);
        log.info("----------------------------------");

        for (LotteryPrize prize : validPrizes) {
            BigDecimal normalized = prize.getProbability()
                    .divide(totalProbability, PROBABILITY_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            ActivityPrize ap = activityPrizeMap.get(prize.getId());
            Long stock = ap != null ? ap.getSurplusStock() : null;

            log.info("奖品: {} | 原始概率: {} | 归一化概率: {}% | 库存: {}",
                    prize.getName(),
                    prize.getProbability(),
                    normalized,
                    stock != null ? stock : "无限制");
        }
        log.info("==================================");
    }
} 