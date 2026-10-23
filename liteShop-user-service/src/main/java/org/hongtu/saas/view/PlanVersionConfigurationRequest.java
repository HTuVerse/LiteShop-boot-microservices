package org.hongtu.saas.view;

import org.hongtu.saas.domain.SaasPlanEntitlement;
import org.hongtu.saas.domain.SaasPlanVersion;

import java.util.List;

/** 套餐草稿价格与权益的一次性保存请求。 */
public record PlanVersionConfigurationRequest(
        SaasPlanVersion version,
        List<SaasPlanEntitlement> entitlements) {
}
