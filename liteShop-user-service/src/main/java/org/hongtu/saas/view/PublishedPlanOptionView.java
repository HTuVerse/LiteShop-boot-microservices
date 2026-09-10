package org.hongtu.saas.view;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class PublishedPlanOptionView {
    private Long planId;
    private Long planVersionId;
    private String planCode;
    private String planName;
    private String description;
    private Integer versionNo;
    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;
    private String currency;
}
