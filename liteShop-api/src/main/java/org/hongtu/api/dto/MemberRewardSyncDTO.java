package org.hongtu.api.dto;

import java.math.BigDecimal;

/** 餐饮订单完成或退款后同步会员奖励所需的订单快照。 */
public record MemberRewardSyncDTO(String orderNo, Long userId, BigDecimal remainingAmount) {}
