package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hongtu.exception.BusinessException;
import org.hongtu.saas.domain.*;
import org.hongtu.saas.mapper.*;
import org.hongtu.saas.service.SaasEntitlementService;
import org.hongtu.saas.service.SaasSubscriptionService;
import org.hongtu.saas.view.EntitlementDecision;
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
}
