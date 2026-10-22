package org.hongtu.params;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AlipayMiniPayParam {
    private Long tenantId;
    private String outTradeNo;
    private String subject;
    private BigDecimal totalAmount;
    private String notifyUrl;
    private String authCode;
}
