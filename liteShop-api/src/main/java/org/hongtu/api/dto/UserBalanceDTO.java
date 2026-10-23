package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 支付预检查只读取余额快照，真正扣减仍由 user-service 原子校验。 */
@Data
@Accessors(chain = true)
public class UserBalanceDTO {
    private Long id;
    private BigDecimal surplusMoney;
}
