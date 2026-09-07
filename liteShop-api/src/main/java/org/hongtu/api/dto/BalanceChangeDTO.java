package org.hongtu.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 余额变动请求（服务间调用）
 * user-service 在一个本地事务内完成：校验余额 -> 更新 sys_user.surplus_money -> 写 user_surplus_record 流水
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceChangeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 变动金额（正数）
     */
    private BigDecimal amount;

    /**
     * 变动类型（对应 BalanceResourceEnum 的 code：1-用户充值 2-管理员充值 3-退款 4-消费）
     */
    private Integer type;

    /**
     * 关联订单号（幂等键，同一订单同一类型的变动只生效一次）
     */
    private String orderNo;

    /**
     * 操作人用户ID（收银台场景为操作管理员）
     */
    private Long operateUserId;
}
