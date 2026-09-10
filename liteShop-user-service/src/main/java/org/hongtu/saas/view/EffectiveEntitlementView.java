package org.hongtu.saas.view;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class EffectiveEntitlementView {
    private Long tenantId;
    private Long subscriptionId;
    private Long planVersionId;
    private String planCode;
    private String planName;
    private Integer planVersion;
    private String subscriptionStatus;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime calculatedAt;
    private Map<String, Item> features = new LinkedHashMap<>();

    @Data
    @Accessors(chain = true)
    public static class Item {
        private Long featureId;
        private String featureCode;
        private String featureName;
        private String featureType;
        private String unit;
        private String quotaMode;
        private String usageSource;
        private String usageMetric;
        private boolean enabled;
        private boolean unlimited;
        private BigDecimal limit;
        private String textValue;
        private BigDecimal used;
        private BigDecimal remaining;
        private String resetPeriod;
        private String enforcement;
        private java.util.List<String> sources;
    }
}
