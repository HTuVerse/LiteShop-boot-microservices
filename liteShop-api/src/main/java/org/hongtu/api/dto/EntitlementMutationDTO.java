package org.hongtu.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EntitlementMutationDTO {
    private String featureCode;
    private BigDecimal amount;
    /** 可选：业务服务已经实时查询到的当前用量；仅 check 使用。 */
    private BigDecimal currentUsage;
    private String idempotencyKey;
    /** 释放时必须引用的原消费幂等键。 */
    private String consumeKey;
}
