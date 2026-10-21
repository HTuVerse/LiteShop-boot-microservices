package org.hongtu.wallet.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 当前租户的储值档位。 */
@Data
public class WalletPlan {
    private Long id;
    private Long tenantId;
    /** 实付金额，元。 */
    private BigDecimal amount;
    /** 赠送金额，元。 */
    private BigDecimal bonus;
    private String badge;
    private Integer sort;
    /** 0停用，1启用。 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
