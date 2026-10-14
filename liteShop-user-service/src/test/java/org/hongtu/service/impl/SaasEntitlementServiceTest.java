package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hongtu.exception.BusinessException;
import org.hongtu.saas.domain.*;
import org.hongtu.saas.mapper.*;
import org.hongtu.saas.service.SaasEntitlementService;
import org.hongtu.saas.service.SaasSubscriptionService;
import org.hongtu.saas.usage.SaasUsageProvider;
import org.hongtu.saas.view.EntitlementDecision;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SaasEntitlementServiceTest {
    @BeforeAll
    static void metadata() {
        for (Class<?> type : List.of(SaasFeature.class, SaasPlan.class, SaasPlanVersion.class,
                SaasPlanEntitlement.class, SaasAddon.class, SaasAddonEntitlement.class,
                SaasTenantSubscription.class, SaasSubscriptionAddon.class,
                SaasTenantEntitlementOverride.class, SaasUsageCounter.class, SaasUsageEvent.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "saas-tests"), type);
        }
    }

    @AfterEach
    void cleanup() { TenantContext.clear(); }

    @Test
    void mergesPlanAddonAndOverrideThenAttachesUsage() {
        SaasFeatureMapper features = mock(SaasFeatureMapper.class);
        SaasPlanMapper plans = mock(SaasPlanMapper.class);
        SaasPlanVersionMapper versions = mock(SaasPlanVersionMapper.class);
        SaasPlanEntitlementMapper planRules = mock(SaasPlanEntitlementMapper.class);
        SaasAddonMapper addons = mock(SaasAddonMapper.class);
        SaasAddonEntitlementMapper addonRules = mock(SaasAddonEntitlementMapper.class);
        SaasUsageCounterMapper counters = mock(SaasUsageCounterMapper.class);
        SaasSubscriptionService subscriptions = mock(SaasSubscriptionService.class);
        SaasEntitlementService service = new SaasEntitlementService();
        ReflectionTestUtils.setField(service, "features", features);
        ReflectionTestUtils.setField(service, "plans", plans);
        ReflectionTestUtils.setField(service, "versions", versions);
        ReflectionTestUtils.setField(service, "planEntitlements", planRules);
        ReflectionTestUtils.setField(service, "addons", addons);
        ReflectionTestUtils.setField(service, "addonEntitlements", addonRules);
        ReflectionTestUtils.setField(service, "subscriptions", subscriptions);
        ReflectionTestUtils.setField(service, "counters", counters);

        TenantContext.setTenantId(10L);
        SaasFeature feature = new SaasFeature().setId(1L).setFeatureCode("member.count")
                .setFeatureName("成员数").setFeatureType("QUOTA").setQuotaMode("RESOURCE").setStatus(1);
        SaasTenantSubscription subscription = new SaasTenantSubscription().setId(20L).setTenantId(10L)
                .setPlanVersionId(30L).setStatus("ACTIVE").setCurrentPeriodStart(LocalDateTime.now().minusDays(1))
                .setCurrentPeriodEnd(LocalDateTime.now().plusDays(29));
        when(features.selectList(any())).thenReturn(List.of(feature));
        when(subscriptions.currentSubscription(10L)).thenReturn(subscription);
        when(versions.selectById(30L)).thenReturn(new SaasPlanVersion().setId(30L).setPlanId(40L).setVersionNo(2));
        when(plans.selectById(40L)).thenReturn(new SaasPlan().setId(40L).setPlanCode("pro").setPlanName("专业版").setStatus(1));
        when(planRules.selectList(any())).thenReturn(List.of(new SaasPlanEntitlement().setFeatureId(1L)
                .setEnabled(1).setQuotaValue(new BigDecimal("20")).setUnlimited(0).setResetPeriod("NONE").setEnforcement("HARD")));
        when(subscriptions.activeAddons(eq(10L), eq(20L), any())).thenReturn(List.of(
                new SaasSubscriptionAddon().setAddonId(50L).setQuantity(2)));
        when(addons.selectList(any())).thenReturn(List.of(new SaasAddon().setId(50L).setAddonCode("seat-pack").setStatus(1)));
        when(addonRules.selectList(any())).thenReturn(List.of(new SaasAddonEntitlement().setAddonId(50L)
                .setFeatureId(1L).setOperation("ADD").setQuotaValue(new BigDecimal("5"))));
        when(subscriptions.activeOverrides(eq(10L), any())).thenReturn(List.of(new SaasTenantEntitlementOverride()
                .setId(60L).setFeatureId(1L).setOperation("ADD").setQuotaValue(new BigDecimal("3")).setReason("合同赠送")));
        when(counters.selectOne(any())).thenReturn(new SaasUsageCounter().setUsedValue(new BigDecimal("12")));

        var item = service.resolve(10L).getFeatures().get("member.count");
        assertTrue(item.isEnabled());
        assertEquals(0, new BigDecimal("33").compareTo(item.getLimit()));
        assertEquals(0, new BigDecimal("12").compareTo(item.getUsed()));
        assertEquals(0, new BigDecimal("21").compareTo(item.getRemaining()));
        assertEquals(List.of("plan:pro@v2", "addon:seat-pack×2", "override:合同赠送"), item.getSources());
    }

    @Test
    void aiManagerCanBeEnabledByAddonForStandardPlan() {
        SaasFeatureMapper features = mock(SaasFeatureMapper.class);
        SaasPlanMapper plans = mock(SaasPlanMapper.class);
        SaasPlanVersionMapper versions = mock(SaasPlanVersionMapper.class);
        SaasPlanEntitlementMapper planRules = mock(SaasPlanEntitlementMapper.class);
        SaasAddonMapper addons = mock(SaasAddonMapper.class);
        SaasAddonEntitlementMapper addonRules = mock(SaasAddonEntitlementMapper.class);
        SaasUsageCounterMapper counters = mock(SaasUsageCounterMapper.class);
        SaasSubscriptionService subscriptions = mock(SaasSubscriptionService.class);
        SaasEntitlementService service = new SaasEntitlementService();
        ReflectionTestUtils.setField(service, "features", features);
        ReflectionTestUtils.setField(service, "plans", plans);
        ReflectionTestUtils.setField(service, "versions", versions);
        ReflectionTestUtils.setField(service, "planEntitlements", planRules);
        ReflectionTestUtils.setField(service, "addons", addons);
        ReflectionTestUtils.setField(service, "addonEntitlements", addonRules);
        ReflectionTestUtils.setField(service, "counters", counters);
        ReflectionTestUtils.setField(service, "subscriptions", subscriptions);

        TenantContext.setTenantId(10L);
        when(features.selectList(any())).thenReturn(List.of(
                new SaasFeature().setId(1L).setFeatureCode(SaasFeatureCode.AI_MANAGER)
                        .setFeatureName("AI 店长").setFeatureType("BOOLEAN")
                        .setDefaultValue(BigDecimal.ZERO).setStatus(1),
                new SaasFeature().setId(2L).setFeatureCode(SaasFeatureCode.AI_MANAGER_CALLS)
                        .setFeatureName("AI 店长生成次数").setFeatureType("NUMBER")
                        .setUsageSource("COUNTER").setStatus(1)));
        when(subscriptions.currentSubscription(10L)).thenReturn(new SaasTenantSubscription()
                .setId(20L).setTenantId(10L).setPlanVersionId(30L).setStatus("ACTIVE"));
        when(versions.selectById(30L)).thenReturn(new SaasPlanVersion().setId(30L).setPlanId(40L).setVersionNo(1));
        when(plans.selectById(40L)).thenReturn(new SaasPlan().setId(40L).setPlanCode("basic")
                .setPlanName("标准版").setStatus(1));
        when(planRules.selectList(any())).thenReturn(List.of());
        when(subscriptions.activeAddons(eq(10L), eq(20L), any())).thenReturn(
                List.of(), List.of(new SaasSubscriptionAddon().setAddonId(50L).setQuantity(1)), List.of());
        when(addons.selectList(any())).thenReturn(List.of(new SaasAddon().setId(50L)
                .setAddonCode("ai_manager").setStatus(1)));
        when(addonRules.selectList(any())).thenReturn(List.of(
                new SaasAddonEntitlement().setAddonId(50L).setFeatureId(1L).setOperation("ENABLE"),
                new SaasAddonEntitlement().setAddonId(50L).setFeatureId(2L).setOperation("ADD")
                        .setQuotaValue(new BigDecimal("100"))));

        assertFalse(service.resolve(10L).getFeatures().get(SaasFeatureCode.AI_MANAGER).isEnabled());
        var purchasedFeatures = service.resolve(10L).getFeatures();
        var purchased = purchasedFeatures.get(SaasFeatureCode.AI_MANAGER);
        assertTrue(purchased.isEnabled());
        assertTrue(purchased.getSources().contains("addon:ai_manager×1"));
        var calls = purchasedFeatures.get(SaasFeatureCode.AI_MANAGER_CALLS);
        assertEquals(new BigDecimal("100"), calls.getLimit());
        assertEquals("BILLING_CYCLE", calls.getResetPeriod());
        assertFalse(service.resolve(10L).getFeatures().get(SaasFeatureCode.AI_MANAGER).isEnabled());
    }

    @Test
    void aiRefundUsesOriginalBillingPeriod() {
        SaasUsageEventMapper events = mock(SaasUsageEventMapper.class);
        SaasUsageCounterMapper counters = mock(SaasUsageCounterMapper.class);
        SaasEntitlementService service = new SaasEntitlementService();
        ReflectionTestUtils.setField(service, "events", events);
        ReflectionTestUtils.setField(service, "counters", counters);
        TenantContext.setTenantId(10L);
        LocalDateTime originalStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(events.selectOne(any())).thenReturn(new SaasUsageEvent().setTenantId(10L)
                .setFeatureCode(SaasFeatureCode.AI_MANAGER_CALLS).setAmount(BigDecimal.ONE)
                .setEventType("CONSUME").setStatus("COMMITTED").setPeriodStart(originalStart), null);
        when(counters.release(10L, SaasFeatureCode.AI_MANAGER_CALLS, originalStart, BigDecimal.ONE))
                .thenReturn(1);

        service.refundConsumption(10L, "ai-call:123", "ai-refund:123");

        verify(counters).release(10L, SaasFeatureCode.AI_MANAGER_CALLS, originalStart, BigDecimal.ONE);
        verify(events).insert(any(SaasUsageEvent.class));
    }

    @Test
    void rejectsCrossTenantResolution() {
        TenantContext.setTenantId(10L);
        assertThrows(BusinessException.class, () -> new SaasEntitlementService().resolve(20L));
    }

    @Test
    void returnsTypedSystemDefaultsWithoutSubscription() {
        SaasFeatureMapper features = mock(SaasFeatureMapper.class);
        SaasUsageCounterMapper counters = mock(SaasUsageCounterMapper.class);
        SaasSubscriptionService subscriptions = mock(SaasSubscriptionService.class);
        SaasEntitlementService service = new SaasEntitlementService();
        ReflectionTestUtils.setField(service, "features", features);
        ReflectionTestUtils.setField(service, "counters", counters);
        ReflectionTestUtils.setField(service, "subscriptions", subscriptions);

        TenantContext.setTenantId(10L);
        when(features.selectList(any())).thenReturn(List.of(
                new SaasFeature().setId(1L).setFeatureCode("member_system").setFeatureName("会员")
                        .setFeatureType("BOOLEAN").setDefaultValue(BigDecimal.ONE).setStatus(1),
                new SaasFeature().setId(2L).setFeatureCode("report_level").setFeatureName("报表等级")
                        .setFeatureType("ENUM").setDefaultTextValue("basic").setStatus(1)));
        when(subscriptions.currentSubscription(10L)).thenReturn(null);

        var result = service.resolve(10L);
        assertTrue(result.getFeatures().get("member_system").isEnabled());
        assertEquals("basic", result.getFeatures().get("report_level").getTextValue());
        assertNull(result.getSubscriptionId());
    }

    @Test
    void checkQuotaCanUseBusinessProvidedRealtimeUsage() {
        SaasFeatureMapper features = mock(SaasFeatureMapper.class);
        SaasUsageCounterMapper counters = mock(SaasUsageCounterMapper.class);
        SaasSubscriptionService subscriptions = mock(SaasSubscriptionService.class);
        SaasEntitlementService service = new SaasEntitlementService();
        ReflectionTestUtils.setField(service, "features", features);
        ReflectionTestUtils.setField(service, "counters", counters);
        ReflectionTestUtils.setField(service, "subscriptions", subscriptions);

        TenantContext.setTenantId(10L);
        when(features.selectList(any())).thenReturn(List.of(new SaasFeature().setId(1L)
                .setFeatureCode("store_count").setFeatureName("门店数").setFeatureType("NUMBER")
                .setQuotaMode("RESOURCE").setUsageSource("COUNTER").setDefaultValue(new BigDecimal("5")).setStatus(1)));
        when(subscriptions.currentSubscription(10L)).thenReturn(null);

        var decision = service.checkQuota(10L, "store_count", new BigDecimal("5"), BigDecimal.ONE);
        assertFalse(decision.isAllowed());
        assertEquals("FEATURE_DISABLED", decision.getReason());
        assertEquals(0, new BigDecimal("5").compareTo(decision.getUsed()));
    }

    @Test
    void consumeRetryChecksIdempotencyBeforeCurrentQuota() {
        SaasUsageEventMapper events = mock(SaasUsageEventMapper.class);
        SaasEntitlementService service = spy(new SaasEntitlementService());
        ReflectionTestUtils.setField(service, "events", events);
        when(events.selectOne(any())).thenReturn(new SaasUsageEvent().setTenantId(10L)
                .setFeatureCode("store_count").setAmount(BigDecimal.ONE)
                .setEventType("CONSUME").setIdempotencyKey("store:create:9").setStatus("COMMITTED"));
        doReturn(new EntitlementDecision().setAllowed(true).setFeatureCode("store_count"))
                .when(service).check(10L, "store_count", BigDecimal.ZERO);

        EntitlementDecision result = service.consume(10L, "store_count", BigDecimal.ONE, "store:create:9");

        assertTrue(result.isAllowed());
        verify(service).check(10L, "store_count", BigDecimal.ZERO);
        verify(service, never()).check(10L, "store_count", BigDecimal.ONE);
        verify(events, never()).insert(any(SaasUsageEvent.class));
    }

    @Test
    void realtimeUsageFailureRejectsQuotaCheckInsteadOfUsingStaleCounter() {
        SaasFeatureMapper features = mock(SaasFeatureMapper.class);
        SaasPlanMapper plans = mock(SaasPlanMapper.class);
        SaasPlanVersionMapper versions = mock(SaasPlanVersionMapper.class);
        SaasPlanEntitlementMapper planRules = mock(SaasPlanEntitlementMapper.class);
        SaasUsageCounterMapper counters = mock(SaasUsageCounterMapper.class);
        SaasSubscriptionService subscriptions = mock(SaasSubscriptionService.class);
        SaasUsageProvider provider = mock(SaasUsageProvider.class);
        SaasEntitlementService service = new SaasEntitlementService();
        ReflectionTestUtils.setField(service, "features", features);
        ReflectionTestUtils.setField(service, "plans", plans);
        ReflectionTestUtils.setField(service, "versions", versions);
        ReflectionTestUtils.setField(service, "planEntitlements", planRules);
        ReflectionTestUtils.setField(service, "subscriptions", subscriptions);
        ReflectionTestUtils.setField(service, "counters", counters);
        ReflectionTestUtils.setField(service, "usageProviders", List.of(provider));

        TenantContext.setTenantId(10L);
        SaasFeature feature = new SaasFeature().setId(1L).setFeatureCode("store_count")
                .setFeatureName("门店数").setFeatureType("NUMBER").setQuotaMode("RESOURCE")
                .setUsageSource("REALTIME").setUsageMetric("store_count").setStatus(1);
        SaasTenantSubscription subscription = new SaasTenantSubscription().setId(20L).setTenantId(10L)
                .setPlanVersionId(30L).setStatus("ACTIVE").setCurrentPeriodStart(LocalDateTime.now().minusDays(1))
                .setCurrentPeriodEnd(LocalDateTime.now().plusDays(29));
        when(features.selectList(any())).thenReturn(List.of(feature));
        when(subscriptions.hasEffectiveSubscription(10L)).thenReturn(true);
        when(subscriptions.currentSubscription(10L)).thenReturn(subscription);
        when(versions.selectById(30L)).thenReturn(new SaasPlanVersion().setId(30L).setPlanId(40L).setVersionNo(1));
        when(plans.selectById(40L)).thenReturn(new SaasPlan().setId(40L).setPlanCode("basic").setPlanName("标准版").setStatus(1));
        when(planRules.selectList(any())).thenReturn(List.of(new SaasPlanEntitlement().setFeatureId(1L)
                .setEnabled(1).setQuotaValue(new BigDecimal("3")).setUnlimited(0)
                .setResetPeriod("NONE").setEnforcement("HARD")));
        when(subscriptions.activeAddons(eq(10L), eq(20L), any())).thenReturn(List.of());
        when(subscriptions.activeOverrides(eq(10L), any())).thenReturn(List.of());
        when(provider.metric()).thenReturn("store_count");
        when(provider.currentUsage(10L)).thenThrow(new IllegalStateException("restaurant unavailable"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.checkQuota(10L, "store_count", null, BigDecimal.ONE));

        assertEquals(org.hongtu.api.constant.SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE, error.getCode());
        assertTrue(error.getMessage().contains("实时用量暂不可用"));
        verify(counters, never()).selectOne(any());
    }

    @Test
    void nonResettingPeriodUsesMysqlSafeWholeSecondUpperBound() {
        Object period = ReflectionTestUtils.invokeMethod(
                new SaasEntitlementService(), "period", "NONE", null);

        LocalDateTime end = (LocalDateTime) ReflectionTestUtils.getField(period, "end");

        assertEquals(LocalDateTime.of(9999, 12, 31, 23, 59, 59), end);
        assertEquals(0, end.getNano());
    }
}
