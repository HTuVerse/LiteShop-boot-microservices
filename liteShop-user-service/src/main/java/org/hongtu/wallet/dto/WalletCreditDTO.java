package org.hongtu.wallet.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 储值成功后的幂等入账命令，金额单位为元。 */
@Data
public class WalletCreditDTO {
    private Long userId;
    private BigDecimal amount;
    private BigDecimal bonus;
    private String orderNo;
}
