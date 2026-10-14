package org.hongtu.saas.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.hongtu.DAO.SysTenantMapper;
import org.hongtu.DTO.SysTenant;
import org.hongtu.exception.BusinessException;
import org.hongtu.saas.domain.*;
import org.hongtu.saas.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class SaasSubscriptionService {
    private static final Set<String> LIVE_STATUSES = Set.of("TRIAL", "TRIALING", "ACTIVE", "PAST_DUE", "GRACE");
    @Resource private SaasCatalogService catalog;
    @Resource private SysTenantMapper tenants;
    @Resource private SaasPlanVersionMapper versions;
    @Resource private SaasTenantSubscriptionMapper subscriptions;
    @Resource private SaasAddonMapper addons;
    @Resource private SaasAddonEntitlementMapper addonEntitlements;
    @Resource private SaasSubscriptionAddonMapper subscriptionAddons;
    @Resource private SaasFeatureMapper features;
    @Resource private SaasTenantEntitlementOverrideMapper overrides;
    @Resource private ObjectMapper objectMapper;

    public List<SaasTenantSubscription> listSubscriptions(Long tenantId) {
        catalog.requirePlatform();
        return subscriptions.selectList(new LambdaQueryWrapper<SaasTenantSubscription>()
                .eq(SaasTenantSubscription::getTenantId, tenantId).orderByDesc(SaasTenantSubscription::getCreateTime));
    }

    public SaasTenantSubscription currentSubscription(Long tenantId) {
        List<SaasTenantSubscription> rows = subscriptions.selectList(new LambdaQueryWrapper<SaasTenantSubscription>()
                .eq(SaasTenantSubscription::getTenantId, tenantId).in(SaasTenantSubscription::getStatus, LIVE_STATUSES)
                .orderByDesc(SaasTenantSubscription::getCreateTime));
        LocalDateTime now = LocalDateTime.now();
        return rows.stream().filter(row -> isEffective(row, now)).findFirst().orElse(null);
    }

    /** 当前租户是否拥有可进入业务后台的有效套餐。 */
    public boolean hasEffectiveSubscription(Long tenantId) {
        return currentSubscription(tenantId) != null;
    }

    public boolean isEffective(SaasTenantSubscription row, LocalDateTime now) {
        if (row == null || !LIVE_STATUSES.contains(row.getStatus()) || (row.getStartAt() != null && row.getStartAt().isAfter(now))) return false;
        if ("TRIAL".equals(row.getStatus()) || "TRIALING".equals(row.getStatus())) return row.getTrialEndAt() == null || !row.getTrialEndAt().isBefore(now);
        if ("GRACE".equals(row.getStatus()) || "PAST_DUE".equals(row.getStatus())) return row.getGraceEndAt() != null && !row.getGraceEndAt().isBefore(now);
        return row.getCurrentPeriodEnd() == null || !row.getCurrentPeriodEnd().isBefore(now);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaasTenantSubscription assign(Long tenantId, SaasTenantSubscription request) {
        catalog.requirePlatform();
        if (tenantId == null || tenants.selectById(tenantId) == null) throw new BusinessException(404, "租户不存在");
        if (request == null || request.getPlanVersionId() == null) throw new BusinessException(400, "请选择套餐版本");
        requirePublishedVersion(request.getPlanVersionId());
        String status = request.getStatus() == null ? "ACTIVE" : request.getStatus();
        if ("TRIAL".equals(status)) status = "TRIALING";
        if (!Set.of("TRIALING", "ACTIVE", "PAST_DUE", "GRACE", "SUSPENDED").contains(status)) throw new BusinessException(400, "订阅状态不正确");
        String billingCycle = request.getBillingCycle() == null ? "MONTHLY" : request.getBillingCycle().trim().toUpperCase();
        if (!Set.of("MONTHLY", "YEARLY", "CUSTOM").contains(billingCycle)) throw new BusinessException(400, "计费周期只能是 MONTHLY、YEARLY 或 CUSTOM");
        LocalDateTime now = LocalDateTime.now();
        // 试用、宽限期订阅必须显式给出结束时间，且不能是过去时间；
        // 否则 isEffective 会判定订阅未生效，造成“分配成功却无任何权益”的静默问题。
        if ("TRIALING".equals(status)) {
            if (request.getTrialEndAt() == null || !request.getTrialEndAt().isAfter(now)) {
                throw new BusinessException(400, "试用订阅必须填写晚于当前时间的试用结束时间");
            }
        }
        if ("GRACE".equals(status) || "PAST_DUE".equals(status)) {
            if (request.getGraceEndAt() == null || !request.getGraceEndAt().isAfter(now)) {
                throw new BusinessException(400, "宽限期订阅必须填写晚于当前时间的宽限期结束时间");
            }
        }
        LocalDateTime periodStart = request.getCurrentPeriodStart() == null ? now : request.getCurrentPeriodStart();
        LocalDateTime periodEnd = request.getCurrentPeriodEnd();
        if (periodEnd == null) {
            if ("CUSTOM".equals(billingCycle)) throw new BusinessException(400, "自定义计费周期必须填写结束时间");
            periodEnd = "YEARLY".equals(billingCycle) ? periodStart.plusYears(1) : periodStart.plusMonths(1);
        }
        subscriptions.update(new LambdaUpdateWrapper<SaasTenantSubscription>()
                .eq(SaasTenantSubscription::getTenantId, tenantId).in(SaasTenantSubscription::getStatus, LIVE_STATUSES)
                .set(SaasTenantSubscription::getStatus, "CANCELED").set(SaasTenantSubscription::getCancelAt, now)
                .set(SaasTenantSubscription::getUpdateTime, now));
        SaasTenantSubscription row = new SaasTenantSubscription().setTenantId(tenantId).setPlanVersionId(request.getPlanVersionId())
                .setBillingCycle(billingCycle).setStatus(status).setStartAt(request.getStartAt() == null ? now : request.getStartAt())
                .setCurrentPeriodStart(periodStart).setCurrentPeriodEnd(periodEnd)
                .setTrialEndAt(request.getTrialEndAt()).setGraceEndAt(request.getGraceEndAt())
                .setCancelAtPeriodEnd(request.getCancelAtPeriodEnd() == null ? 0 : request.getCancelAtPeriodEnd())
                .setAutoRenew(request.getAutoRenew() == null ? 0 : request.getAutoRenew())
                .setExternalSubscriptionId(request.getExternalSubscriptionId()).setCreateTime(now).setUpdateTime(now);
        if (!row.getCurrentPeriodEnd().isAfter(row.getCurrentPeriodStart())) throw new BusinessException(400, "计费周期结束时间必须晚于开始时间");
        subscriptions.insert(row);
        LocalDateTime accessEnd = switch (status) {
            case "TRIALING" -> row.getTrialEndAt() == null ? row.getCurrentPeriodEnd() : row.getTrialEndAt();
            case "GRACE", "PAST_DUE" -> row.getGraceEndAt() == null ? row.getCurrentPeriodEnd() : row.getGraceEndAt();
            // 暂停订阅不产生任何生效权益，访问截止时间同步设为当前时间，避免“无权益却仍可登录”的状态不一致。
            case "SUSPENDED" -> now;
            default -> row.getCurrentPeriodEnd();
        };
        tenants.updateById(new SysTenant().setId(tenantId).setExpireTime(accessEnd).setUpdateTime(now));
        return row;
    }

    public SaasPlanVersion requirePublishedVersion(Long planVersionId) {
        catalog.requirePlatform();
        if (planVersionId == null) throw new BusinessException(400, "请选择套餐版本");
        SaasPlanVersion version = versions.selectById(planVersionId);
        if (version == null || !"PUBLISHED".equals(version.getStatus())) {
            throw new BusinessException(400, "只能订阅已发布的套餐版本");
        }
        return version;
    }

    public List<SaasSubscriptionAddon> listAddons(Long tenantId) {
        catalog.requirePlatform();
        return subscriptionAddons.selectList(new LambdaQueryWrapper<SaasSubscriptionAddon>()
                .eq(SaasSubscriptionAddon::getTenantId, tenantId).orderByDesc(SaasSubscriptionAddon::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public SaasSubscriptionAddon addAddon(Long tenantId, SaasSubscriptionAddon request) {
        catalog.requirePlatform();
        SaasTenantSubscription subscription = currentSubscription(tenantId);
        if (subscription == null) throw new BusinessException(409, "租户当前没有有效订阅");
        if (request == null || request.getAddonId() == null) throw new BusinessException(400, "请选择加购项");
        SaasAddon addon = addons.selectById(request.getAddonId());
        if (addon == null || !Integer.valueOf(1).equals(addon.getStatus())) throw new BusinessException(400, "加购项不存在或已停用");
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        if (quantity < 1 || quantity > addon.getMaxQuantity()) throw new BusinessException(400, "加购数量超出允许范围");
        LocalDateTime now = LocalDateTime.now();
        SaasSubscriptionAddon row = new SaasSubscriptionAddon().setTenantId(tenantId).setSubscriptionId(subscription.getId())
                .setAddonId(addon.getId()).setQuantity(quantity).setStatus("ACTIVE")
                .setStartAt(request.getStartAt() == null ? now : request.getStartAt()).setEndAt(request.getEndAt())
                .setExternalItemId(request.getExternalItemId()).setEntitlementSnapshot(addonSnapshot(addon.getId()))
                .setCreateTime(now).setUpdateTime(now);
        subscriptionAddons.insert(row);
        return row;
    }

    public void cancelAddon(Long tenantId, Long id) {
        catalog.requirePlatform();
        int updated = subscriptionAddons.update(new LambdaUpdateWrapper<SaasSubscriptionAddon>()
                .eq(SaasSubscriptionAddon::getId, id).eq(SaasSubscriptionAddon::getTenantId, tenantId)
                .set(SaasSubscriptionAddon::getStatus, "CANCELED").set(SaasSubscriptionAddon::getEndAt, LocalDateTime.now())
                .set(SaasSubscriptionAddon::getUpdateTime, LocalDateTime.now()));
        if (updated != 1) throw new BusinessException(404, "租户加购记录不存在");
    }

    public List<SaasTenantEntitlementOverride> listOverrides(Long tenantId) {
        catalog.requirePlatform();
        return overrides.selectList(new LambdaQueryWrapper<SaasTenantEntitlementOverride>()
                .eq(SaasTenantEntitlementOverride::getTenantId, tenantId).orderByDesc(SaasTenantEntitlementOverride::getCreateTime));
    }

    public SaasTenantEntitlementOverride saveOverride(Long tenantId, SaasTenantEntitlementOverride row) {
        catalog.requirePlatform();
        if (tenants.selectById(tenantId) == null) throw new BusinessException(404, "租户不存在");
        if (row == null || row.getFeatureId() == null || features.selectById(row.getFeatureId()) == null) throw new BusinessException(400, "功能不存在");
        SaasFeature feature = features.selectById(row.getFeatureId());
        catalog.requireSellableFeature(feature);
        if (!Set.of("ENABLE", "DISABLE", "ADD", "SET").contains(row.getOperation())) throw new BusinessException(400, "权益调整操作不正确");
        catalog.validateOperation(feature, row.getOperation(), row.getQuotaValue(), row.getTextValue(), "租户权益调整");
        if (row.getEffectiveFrom() != null && row.getEffectiveTo() != null && !row.getEffectiveTo().isAfter(row.getEffectiveFrom())) {
            throw new BusinessException(400, "调整失效时间必须晚于生效时间");
        }
        row.setId(null).setTenantId(tenantId).setTextValue(row.getTextValue() == null ? null : row.getTextValue().trim())
                .setCreatedBy(loginId()).setCreateTime(LocalDateTime.now());
        overrides.insert(row);
        return row;
    }

    public void deleteOverride(Long tenantId, Long id) {
        catalog.requirePlatform();
        if (overrides.delete(new LambdaQueryWrapper<SaasTenantEntitlementOverride>()
                .eq(SaasTenantEntitlementOverride::getTenantId, tenantId).eq(SaasTenantEntitlementOverride::getId, id)) != 1) {
            throw new BusinessException(404, "权益调整记录不存在");
        }
    }

    public List<SaasSubscriptionAddon> activeAddons(Long tenantId, Long subscriptionId, LocalDateTime now) {
        return subscriptionAddons.selectList(new LambdaQueryWrapper<SaasSubscriptionAddon>()
                .eq(SaasSubscriptionAddon::getTenantId, tenantId).eq(SaasSubscriptionAddon::getSubscriptionId, subscriptionId)
                .eq(SaasSubscriptionAddon::getStatus, "ACTIVE").le(SaasSubscriptionAddon::getStartAt, now)
                .and(w -> w.isNull(SaasSubscriptionAddon::getEndAt).or().ge(SaasSubscriptionAddon::getEndAt, now)));
    }

    public List<SaasTenantEntitlementOverride> activeOverrides(Long tenantId, LocalDateTime now) {
        return overrides.selectList(new LambdaQueryWrapper<SaasTenantEntitlementOverride>()
                .eq(SaasTenantEntitlementOverride::getTenantId, tenantId)
                .and(w -> w.isNull(SaasTenantEntitlementOverride::getEffectiveFrom).or().le(SaasTenantEntitlementOverride::getEffectiveFrom, now))
                .and(w -> w.isNull(SaasTenantEntitlementOverride::getEffectiveTo).or().ge(SaasTenantEntitlementOverride::getEffectiveTo, now))
                .orderByAsc(SaasTenantEntitlementOverride::getCreateTime));
    }

    private Long loginId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception ignored) { return null; }
    }

    private String addonSnapshot(Long addonId) {
        List<SaasAddonEntitlement> rules = addonEntitlements.selectList(new LambdaQueryWrapper<SaasAddonEntitlement>()
                .eq(SaasAddonEntitlement::getAddonId, addonId).orderByAsc(SaasAddonEntitlement::getId));
        if (rules.isEmpty()) throw new BusinessException(409, "加购项尚未配置任何权益");
        try { return objectMapper.writeValueAsString(rules); }
        catch (Exception error) { throw new BusinessException(500, "生成加购权益快照失败", error); }
    }
}
