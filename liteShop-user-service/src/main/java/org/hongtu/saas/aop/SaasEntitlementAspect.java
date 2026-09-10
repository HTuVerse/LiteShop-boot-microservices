package org.hongtu.saas.aop;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hongtu.exception.BusinessException;
import org.hongtu.saas.annotation.RequireFeature;
import org.hongtu.saas.annotation.RequireQuota;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.saas.service.EntitlementService;
import org.hongtu.saas.view.EntitlementDecision;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Aspect
@Component
@RequiredArgsConstructor
public class SaasEntitlementAspect {
    private final EntitlementService entitlements;

    @Around("@annotation(rule)")
    public Object requireFeature(ProceedingJoinPoint point, RequireFeature rule) throws Throwable {
        checkPermission(rule.permission());
        Long tenantId = TenantContext.getTenantId();
        if (!entitlements.hasFeature(tenantId, rule.value())) {
            throw new BusinessException(SaasErrorCode.FEATURE_DISABLED,
                    "当前套餐未开通功能 " + rule.value() + "，请升级套餐或购买对应加购项");
        }
        return point.proceed();
    }

    @Around("@annotation(rule)")
    public Object requireQuota(ProceedingJoinPoint point, RequireQuota rule) throws Throwable {
        checkPermission(rule.permission());
        BigDecimal amount;
        try { amount = new BigDecimal(rule.amount()); }
        catch (NumberFormatException error) { throw new IllegalStateException("@RequireQuota amount 必须是数字", error); }
        EntitlementDecision decision = entitlements.checkQuota(TenantContext.getTenantId(), rule.value(), null, amount);
        if (!decision.isAllowed()) {
            throw new BusinessException(SaasErrorCode.QUOTA_EXCEEDED, quotaMessage(rule.value(), decision));
        }
        return point.proceed();
    }

    private void checkPermission(String permission) {
        if (permission != null && !permission.isBlank()) StpUtil.checkPermission(permission);
    }

    private String quotaMessage(String featureCode, EntitlementDecision decision) {
        if ("FEATURE_DISABLED".equals(decision.getReason())) {
            return "当前套餐未开通配额 " + featureCode + "，请升级套餐或购买对应加购项";
        }
        return "当前套餐的 " + featureCode + " 上限为 " + decision.getLimit()
                + "，当前已使用 " + decision.getUsed() + "，请升级套餐或购买额外额度";
    }
}
