package org.hongtu.saas.service;

import org.hongtu.saas.view.EffectiveEntitlementView;
import org.hongtu.saas.view.EntitlementDecision;

import java.math.BigDecimal;

/** 业务模块统一依赖的租户权益门面。 */
public interface EntitlementService {
    EffectiveEntitlementView getEffectiveEntitlements(Long tenantId);
    boolean hasFeature(Long tenantId, String featureCode);
    BigDecimal getLimit(Long tenantId, String featureCode);
    String getTextValue(Long tenantId, String featureCode);
    EntitlementDecision checkQuota(Long tenantId, String featureCode, BigDecimal currentUsage, BigDecimal requestedAmount);
}
