package org.hongtu.saas.view;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class EntitlementDecision {
    private boolean allowed;
    private String featureCode;
    private BigDecimal limit;
    private BigDecimal used;
    private BigDecimal remaining;
    private String reason;
    private boolean upgradeRequired;
}
