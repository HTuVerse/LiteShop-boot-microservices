package org.hongtu.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.exception.BusinessException;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.saas.constant.SaasFeatureType;
import org.hongtu.saas.constant.SaasFeatureKeys;
import org.hongtu.saas.domain.*;
import org.hongtu.saas.mapper.*;
import org.hongtu.saas.usage.SaasUsageProvider;
import org.hongtu.saas.view.EffectiveEntitlementView;
import org.hongtu.saas.view.EntitlementDecision;
import org.hongtu.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class SaasEntitlementService implements EntitlementService {
    @Resource private SaasFeatureMapper features;
    @Resource private SaasPlanMapper plans;
    @Resource private SaasPlanVersionMapper versions;
    @Resource private SaasPlanEntitlementMapper planEntitlements;
    @Resource private SaasAddonMapper addons;
    @Resource private SaasAddonEntitlementMapper addonEntitlements;
    @Resource private SaasSubscriptionService subscriptions;
    @Resource private SaasUsageCounterMapper counters;
    @Resource private SaasUsageEventMapper events;
    @Resource private ObjectMapper objectMapper;
    @Autowired(required = false) private List<SaasUsageProvider> usageProviders = List.of();

    public EffectiveEntitlementView resolveCurrent() {
        return resolve(TenantContext.getTenantId());
    }

    @Override
    public EffectiveEntitlementView getEffectiveEntitlements(Long tenantId) { return resolve(tenantId); }

    @Override
    public boolean hasFeature(Long tenantId, String featureCode) {
        if (!subscriptions.hasEffectiveSubscription(tenantId)) return false;
        EffectiveEntitlementView.Item item = resolve(tenantId).getFeatures().get(featureCode);
        return item != null && item.isEnabled();
    }

    @Override
    public BigDecimal getLimit(Long tenantId, String featureCode) {
        if (!subscriptions.hasEffectiveSubscription(tenantId)) return null;
        EffectiveEntitlementView.Item item = resolve(tenantId).getFeatures().get(featureCode);
        return item == null || !item.isEnabled() || item.isUnlimited() ? null : item.getLimit();
    }

    @Override
    public String getTextValue(Long tenantId, String featureCode) {
        if (!subscriptions.hasEffectiveSubscription(tenantId)) return null;
        EffectiveEntitlementView.Item item = resolve(tenantId).getFeatures().get(featureCode);
        return item == null || !item.isEnabled() ? null : item.getTextValue();
    }

    @Override
    public EntitlementDecision checkQuota(Long tenantId, String featureCode, BigDecimal currentUsage, BigDecimal requestedAmount) {
        BigDecimal requested = requestedAmount == null ? BigDecimal.ZERO : requestedAmount;
        if (requested.signum() < 0) throw new BusinessException(400, "检查数量不能为负数");
        if (!subscriptions.hasEffectiveSubscription(tenantId)) {
            BigDecimal used = zero(currentUsage);
            return new EntitlementDecision().setFeatureCode(featureCode)
                    .setAllowed(false).setUsed(used).setRemaining(BigDecimal.ZERO)
                    .setReason("FEATURE_DISABLED").setUpgradeRequired(true);
        }
        EffectiveEntitlementView.Item item = resolveForQuota(tenantId).getFeatures().get(featureCode);
        return decide(item, featureCode, currentUsage == null && item != null ? item.getUsed() : currentUsage, requested);
    }

    public EffectiveEntitlementView resolve(Long tenantId) {
        return resolve(tenantId, false);
    }

    /** 配额变更必须拿到可信的实时用量，不能使用可能落后的计数器继续放行。 */
    private EffectiveEntitlementView resolveForQuota(Long tenantId) {
        return resolve(tenantId, true);
    }

    private EffectiveEntitlementView resolve(Long tenantId, boolean requireRealtimeUsage) {
        assertTenantAccess(tenantId);
        LocalDateTime now = LocalDateTime.now();
        EffectiveEntitlementView result = new EffectiveEntitlementView().setTenantId(tenantId).setCalculatedAt(now);
        Map<Long, SaasFeature> featureById = new LinkedHashMap<>();
        features.selectList(new LambdaQueryWrapper<SaasFeature>().eq(SaasFeature::getStatus, 1).orderByAsc(SaasFeature::getFeatureCode))
                .forEach(feature -> featureById.put(feature.getId(), feature));

        // System Default 是最低优先级；无订阅时也返回目录，前端可据此展示升级入口。
        featureById.values().forEach(feature -> result.getFeatures().put(feature.getFeatureCode(), item(feature)));

        SaasTenantSubscription subscription = subscriptions.currentSubscription(tenantId);
        if (subscription == null) { attachUsage(result, null, requireRealtimeUsage); return result; }
        SaasPlanVersion version = versions.selectById(subscription.getPlanVersionId());
        SaasPlan plan = version == null ? null : plans.selectById(version.getPlanId());
        if (version == null || plan == null || !Integer.valueOf(1).equals(plan.getStatus())) {
            attachUsage(result, subscription, requireRealtimeUsage);
            return result;
        }
        result.setSubscriptionId(subscription.getId()).setPlanVersionId(version.getId()).setPlanVersion(version.getVersionNo())
                .setPlanCode(plan.getPlanCode()).setPlanName(plan.getPlanName()).setSubscriptionStatus(subscription.getStatus())
                .setPeriodStart(subscription.getCurrentPeriodStart()).setPeriodEnd(subscription.getCurrentPeriodEnd());

        for (SaasPlanEntitlement rule : planEntitlements.selectList(new LambdaQueryWrapper<SaasPlanEntitlement>()
                .eq(SaasPlanEntitlement::getPlanVersionId, version.getId()).orderByAsc(SaasPlanEntitlement::getId))) {
            SaasFeature feature = featureById.get(rule.getFeatureId());
            if (feature == null) continue;
            EffectiveEntitlementView.Item item = item(feature);
            item.setEnabled(Integer.valueOf(1).equals(rule.getEnabled()))
                    .setUnlimited(Integer.valueOf(1).equals(rule.getUnlimited()))
                    .setLimit(rule.getQuotaValue()).setTextValue(rule.getTextValue())
                    .setResetPeriod(value(rule.getResetPeriod(), "NONE"))
                    .setEnforcement(value(rule.getEnforcement(), "HARD"))
                    .setSources(new ArrayList<>(List.of("plan:" + plan.getPlanCode() + "@v" + version.getVersionNo())));
            result.getFeatures().put(feature.getFeatureCode(), item);
        }

        applyAddons(result, featureById, subscriptions.activeAddons(tenantId, subscription.getId(), now));
        applyOverrides(result, featureById, subscriptions.activeOverrides(tenantId, now));
        attachUsage(result, subscription, requireRealtimeUsage);
        return result;
    }

    private void applyAddons(EffectiveEntitlementView result, Map<Long, SaasFeature> featureById,
                             List<SaasSubscriptionAddon> active) {
        if (active.isEmpty()) return;
        Set<Long> addonIds = new LinkedHashSet<>(active.stream().map(SaasSubscriptionAddon::getAddonId).toList());
        Map<Long, SaasAddon> addonById = new HashMap<>();
        // 加购项下架（status=0）只应影响“能否新购”，已购记录的权益由 entitlement_snapshot 固化，
        // 这里不再按 status 过滤，避免停用加购项导致租户已付费权益被静默移除。
        addons.selectList(new LambdaQueryWrapper<SaasAddon>().in(SaasAddon::getId, addonIds))
                .forEach(row -> addonById.put(row.getId(), row));
        if (addonById.isEmpty()) return;
        Map<Long, List<SaasAddonEntitlement>> liveRules = new HashMap<>();
        for (SaasAddonEntitlement rule : addonEntitlements.selectList(new LambdaQueryWrapper<SaasAddonEntitlement>()
                .in(SaasAddonEntitlement::getAddonId, addonById.keySet()).orderByAsc(SaasAddonEntitlement::getId))) {
            liveRules.computeIfAbsent(rule.getAddonId(), ignored -> new ArrayList<>()).add(rule);
        }
        for (SaasSubscriptionAddon purchase : active) {
            SaasAddon addon = addonById.get(purchase.getAddonId());
            if (addon == null) continue;
            List<SaasAddonEntitlement> rules = snapshotRules(purchase, liveRules.getOrDefault(purchase.getAddonId(), List.of()));
            for (SaasAddonEntitlement rule : rules) {
                SaasFeature feature = featureById.get(rule.getFeatureId());
                if (feature == null) continue;
                EffectiveEntitlementView.Item item = result.getFeatures().computeIfAbsent(feature.getFeatureCode(), ignored -> item(feature));
                apply(item, rule.getOperation(), multiply(rule.getQuotaValue(), purchase.getQuantity()), rule.getTextValue());
                item.getSources().add("addon:" + addon.getAddonCode() + "×" + purchase.getQuantity());
            }
        }
    }

    private List<SaasAddonEntitlement> snapshotRules(SaasSubscriptionAddon purchase, List<SaasAddonEntitlement> fallback) {
        if (purchase.getEntitlementSnapshot() == null || purchase.getEntitlementSnapshot().isBlank()) return fallback;
        try { return objectMapper.readValue(purchase.getEntitlementSnapshot(), new TypeReference<>() {}); }
        catch (Exception error) { throw new BusinessException(500, "加购权益快照损坏：" + purchase.getId(), error); }
    }

    private void applyOverrides(EffectiveEntitlementView result, Map<Long, SaasFeature> featureById,
                                List<SaasTenantEntitlementOverride> active) {
        for (SaasTenantEntitlementOverride rule : active) {
            SaasFeature feature = featureById.get(rule.getFeatureId());
            if (feature == null) continue;
            EffectiveEntitlementView.Item item = result.getFeatures().computeIfAbsent(feature.getFeatureCode(), ignored -> item(feature));
            apply(item, rule.getOperation(), rule.getQuotaValue(), rule.getTextValue());
            item.getSources().add("override:" + (rule.getReason() == null ? rule.getId() : rule.getReason()));
        }
    }

    private void apply(EffectiveEntitlementView.Item item, String operation, BigDecimal value, String textValue) {
        switch (operation) {
            case "ENABLE" -> item.setEnabled(true);
            case "DISABLE" -> item.setEnabled(false);
            case "ADD" -> {
                if (!SaasFeatureType.numeric(item.getFeatureType())) throw new BusinessException(500, "非数值功能不能执行 ADD：" + item.getFeatureCode());
                item.setEnabled(true);
                if (!item.isUnlimited()) item.setLimit(zero(item.getLimit()).add(zero(value)));
            }
            case "SET" -> {
                item.setEnabled(true).setUnlimited(false);
                if (SaasFeatureType.numeric(item.getFeatureType())) item.setLimit(zero(value));
                else item.setTextValue(textValue);
            }
            default -> throw new BusinessException(500, "存在无法识别的权益操作：" + operation);
        }
    }

    private void attachUsage(EffectiveEntitlementView result, SaasTenantSubscription subscription,
                             boolean requireRealtimeUsage) {
        for (EffectiveEntitlementView.Item item : result.getFeatures().values()) {
            if (!SaasFeatureType.numeric(item.getFeatureType())) continue;
            if ("NONE".equals(item.getUsageSource())) {
                BigDecimal used = BigDecimal.ZERO;
                item.setUsed(used).setRemaining(item.isUnlimited() || item.getLimit() == null
                        ? null : item.getLimit().subtract(used).max(BigDecimal.ZERO));
                continue;
            }
            Period period = period(item.getResetPeriod(), subscription);
            BigDecimal used = realtimeUsage(result.getTenantId(), item, requireRealtimeUsage);
            if (used == null) {
                SaasUsageCounter counter = counters.selectOne(new LambdaQueryWrapper<SaasUsageCounter>()
                        .eq(SaasUsageCounter::getTenantId, result.getTenantId()).eq(SaasUsageCounter::getFeatureCode, item.getFeatureCode())
                        .eq(SaasUsageCounter::getPeriodStart, period.start()));
                used = counter == null ? BigDecimal.ZERO : counter.getUsedValue();
            }
            item.setUsed(used).setRemaining(item.isUnlimited() || item.getLimit() == null ? null : item.getLimit().subtract(used).max(BigDecimal.ZERO));
        }
    }

    private BigDecimal realtimeUsage(Long tenantId, EffectiveEntitlementView.Item item,
                                     boolean requireRealtimeUsage) {
        if (!"REALTIME".equals(item.getUsageSource()) || item.getUsageMetric() == null) return null;
        Optional<SaasUsageProvider> provider = usageProviders.stream()
                .filter(candidate -> item.getUsageMetric().equals(candidate.metric())).findFirst();
        if (provider.isEmpty()) {
            if (requireRealtimeUsage) {
                throw new BusinessException(SaasErrorCode.ENTITLEMENT_CONFIGURATION_INVALID,
                        "实时用量提供器未配置，为防止超额本次操作已拒绝");
            }
            return null;
        }
        try {
            BigDecimal usage = provider.get().currentUsage(tenantId);
            if (usage == null && requireRealtimeUsage) {
                throw new BusinessException(SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE,
                        "实时用量暂不可用，为防止超额本次操作已拒绝");
            }
            return usage;
        } catch (RuntimeException error) {
            if (requireRealtimeUsage) {
                if (error instanceof BusinessException businessException) throw businessException;
                throw new BusinessException(SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE,
                        "实时用量暂不可用，为防止超额本次操作已拒绝", error);
            }
            log.warn("实时用量读取失败，回退原子计数器 tenantId={}, featureCode={}, metric={}",
                    tenantId, item.getFeatureCode(), item.getUsageMetric(), error);
            return null;
        }
    }

    public EntitlementDecision check(Long tenantId, String featureCode, BigDecimal amount) {
        BigDecimal requested = amount == null ? BigDecimal.ZERO : amount;
        if (requested.signum() < 0) throw new BusinessException(400, "检查数量不能为负数");
        EffectiveEntitlementView.Item item = resolveForQuota(tenantId).getFeatures().get(featureCode);
        return decide(item, featureCode, item == null ? null : item.getUsed(), requested);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntitlementDecision consume(Long tenantId, String featureCode, BigDecimal amount, String idempotencyKey) {
        validateMutation(featureCode, amount, idempotencyKey);
        SaasUsageEvent existing = existingEvent(tenantId, idempotencyKey);
        if (existing != null) {
            return sameEvent(existing, featureCode, amount, "CONSUME")
                    ? check(tenantId, featureCode, BigDecimal.ZERO) : conflictKey();
        }
        EntitlementDecision before = check(tenantId, featureCode, amount);
        if (!before.isAllowed()) return before;
        SaasTenantSubscription subscription = subscriptions.currentSubscription(tenantId);
        EffectiveEntitlementView.Item item = resolveForQuota(tenantId).getFeatures().get(featureCode);
        Period period = period(item.getResetPeriod(), subscription);
        SaasUsageEvent event = new SaasUsageEvent().setTenantId(tenantId).setFeatureCode(featureCode).setAmount(amount)
                .setEventType("CONSUME").setIdempotencyKey(idempotencyKey).setPeriodStart(period.start()).setStatus("PENDING")
                .setCreateTime(LocalDateTime.now());
        try { events.insert(event); }
        catch (DuplicateKeyException duplicate) {
            SaasUsageEvent raced = existingEvent(tenantId, idempotencyKey);
            return raced != null && sameEvent(raced, featureCode, amount, "CONSUME") ? check(tenantId, featureCode, BigDecimal.ZERO) : conflictKey();
        }
        // 实时资源在业务库中已有存量时，先把原子计数器提升到当前真实值。
        // GREATEST + 后续条件更新位于同一事务，可阻止两个并发创建同时越过最后一个名额。
        if ("REALTIME".equals(item.getUsageSource())) {
            counters.ensureAtLeast(IdWorker.getId(), tenantId, featureCode, period.start(), period.end(), zero(item.getUsed()));
        }
        int changed = item.isUnlimited() || "SOFT".equals(item.getEnforcement())
                ? counters.consumeUnlimited(IdWorker.getId(), tenantId, featureCode, period.start(), period.end(), amount)
                : counters.tryConsume(IdWorker.getId(), tenantId, featureCode, period.start(), period.end(), amount, item.getLimit());
        if (changed == 0) throw new BusinessException(SaasErrorCode.QUOTA_EXCEEDED, "并发请求导致配额不足，请刷新后重试");
        events.update(new LambdaUpdateWrapper<SaasUsageEvent>().eq(SaasUsageEvent::getId, event.getId()).set(SaasUsageEvent::getStatus, "COMMITTED"));
        return check(tenantId, featureCode, BigDecimal.ZERO);
    }

    /** 释放必须关联原消费事件，沿用原计费周期且最多释放一次。 */
    @Transactional(rollbackFor = Exception.class)
    public void refundConsumption(Long tenantId, String consumeKey, String refundKey) {
        assertTenantAccess(tenantId);
        SaasUsageEvent consumed = events.selectOne(new LambdaQueryWrapper<SaasUsageEvent>()
                .eq(SaasUsageEvent::getTenantId, tenantId)
                .eq(SaasUsageEvent::getIdempotencyKey, consumeKey).last("FOR UPDATE"));
        if (consumed == null || !"CONSUME".equals(consumed.getEventType())
                || !"COMMITTED".equals(consumed.getStatus())) {
            throw new BusinessException(409, "原消费记录不存在");
        }
        SaasUsageEvent existing = existingEvent(tenantId, refundKey);
        if (existing != null) return;
        SaasUsageEvent refunded = new SaasUsageEvent().setTenantId(tenantId)
                .setFeatureCode(consumed.getFeatureCode()).setAmount(consumed.getAmount())
                .setEventType("RELEASE").setIdempotencyKey(refundKey)
                .setPeriodStart(consumed.getPeriodStart()).setStatus("PENDING")
                .setCreateTime(LocalDateTime.now());
        events.insert(refunded);
        if (counters.release(tenantId, consumed.getFeatureCode(), consumed.getPeriodStart(), consumed.getAmount()) != 1) {
            throw new BusinessException(500, "功能 " + consumed.getFeatureCode() + " 额度退款失败");
        }
        events.update(new LambdaUpdateWrapper<SaasUsageEvent>().eq(SaasUsageEvent::getId, refunded.getId())
                .set(SaasUsageEvent::getStatus, "COMMITTED"));
    }

    private EffectiveEntitlementView.Item item(SaasFeature feature) {
        return new EffectiveEntitlementView.Item().setFeatureId(feature.getId()).setFeatureCode(feature.getFeatureCode())
                .setFeatureName(feature.getFeatureName()).setFeatureType(feature.getFeatureType()).setUnit(feature.getUnit())
                .setQuotaMode(feature.getQuotaMode())
                .setUsageSource(value(feature.getUsageSource(), SaasFeatureType.numeric(feature.getFeatureType()) ? "COUNTER" : "NONE"))
                .setUsageMetric(feature.getUsageMetric()).setEnabled(defaultEnabled(feature)).setUnlimited(false)
                .setLimit(feature.getDefaultValue()).setTextValue(feature.getDefaultTextValue())
                .setUsed(BigDecimal.ZERO)
                .setResetPeriod(SaasFeatureKeys.AI_MANAGER_CALLS.equals(feature.getFeatureCode()) ? "BILLING_CYCLE" : "NONE")
                .setEnforcement("HARD").setSources(new ArrayList<>());
    }

    private boolean defaultEnabled(SaasFeature feature) {
        if ("BOOLEAN".equals(SaasFeatureType.normalize(feature.getFeatureType()))) {
            return feature.getDefaultValue() != null && feature.getDefaultValue().signum() != 0;
        }
        if (SaasFeatureType.numeric(feature.getFeatureType())) return feature.getDefaultValue() != null;
        return feature.getDefaultTextValue() != null;
    }

    private EntitlementDecision decide(EffectiveEntitlementView.Item item, String featureCode,
                                       BigDecimal currentUsage, BigDecimal requested) {
        EntitlementDecision decision = new EntitlementDecision().setFeatureCode(featureCode);
        if (item == null || !item.isEnabled()) {
            return decision.setAllowed(false).setReason("FEATURE_DISABLED").setUpgradeRequired(true);
        }
        BigDecimal used = zero(currentUsage);
        BigDecimal remaining = item.isUnlimited() || item.getLimit() == null
                ? null : item.getLimit().subtract(used).max(BigDecimal.ZERO);
        decision.setLimit(item.getLimit()).setUsed(used).setRemaining(remaining);
        if (!SaasFeatureType.numeric(item.getFeatureType()) || item.isUnlimited()) {
            return decision.setAllowed(true).setReason("ALLOWED");
        }
        boolean exceeded = item.getLimit() == null || used.add(requested).compareTo(item.getLimit()) > 0;
        if (!exceeded) return decision.setAllowed(true).setReason("ALLOWED");
        if ("SOFT".equals(item.getEnforcement())) {
            return decision.setAllowed(true).setReason("QUOTA_SOFT_EXCEEDED").setUpgradeRequired(true);
        }
        return decision.setAllowed(false).setReason("QUOTA_EXCEEDED").setUpgradeRequired(true);
    }

    private Period period(String resetPeriod, SaasTenantSubscription subscription) {
        LocalDateTime now = LocalDateTime.now();
        return switch (value(resetPeriod, "NONE")) {
            case "DAY" -> new Period(LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
            case "MONTH" -> new Period(LocalDate.now().withDayOfMonth(1).atStartOfDay(), LocalDate.now().withDayOfMonth(1).plusMonths(1).atStartOfDay());
            case "BILLING_CYCLE" -> subscription != null && subscription.getCurrentPeriodStart() != null && subscription.getCurrentPeriodEnd() != null
                    ? new Period(subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd())
                    : new Period(LocalDate.now().withDayOfMonth(1).atStartOfDay(), LocalDate.now().withDayOfMonth(1).plusMonths(1).atStartOfDay());
            // period_end 是 DATETIME(0)，不能使用 LocalTime.MAX；小数秒四舍五入会越界到公元 10000 年。
            default -> new Period(LocalDate.of(1970, 1, 1).atStartOfDay(),
                    LocalDate.of(9999, 12, 31).atTime(23, 59, 59));
        };
    }

    private void assertTenantAccess(Long tenantId) {
        if (tenantId == null || tenantId <= 0) throw new BusinessException(400, "租户ID不正确");
        Long current = TenantContext.getTenantIdOrNull();
        if (current != null && !Objects.equals(current, tenantId)) throw new BusinessException(403, "不能访问其他租户权益");
        if (current == null && !TenantContext.isSuperAdmin()) throw new BusinessException(403, "缺少租户上下文");
    }

    private void validateMutation(String featureCode, BigDecimal amount, String idempotencyKey) {
        if (featureCode == null || featureCode.isBlank() || amount == null || amount.signum() <= 0) throw new BusinessException(400, "功能编码和正数额度不能为空");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) throw new BusinessException(400, "幂等键不能为空且不能超过128字符");
    }

    private SaasUsageEvent existingEvent(Long tenantId, String key) {
        return events.selectOne(new LambdaQueryWrapper<SaasUsageEvent>().eq(SaasUsageEvent::getTenantId, tenantId)
                .eq(SaasUsageEvent::getIdempotencyKey, key));
    }

    private boolean sameEvent(SaasUsageEvent event, String feature, BigDecimal amount, String type) {
        return Objects.equals(event.getFeatureCode(), feature) && Objects.equals(event.getEventType(), type)
                && event.getAmount() != null && event.getAmount().compareTo(amount) == 0;
    }

    private EntitlementDecision conflictKey() { throw new BusinessException(409, "幂等键已被其他用量操作使用"); }
    private BigDecimal multiply(BigDecimal value, Integer quantity) { return value == null ? null : value.multiply(BigDecimal.valueOf(quantity)); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private record Period(LocalDateTime start, LocalDateTime end) {}
}
