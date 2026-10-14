package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.saas.domain.SaasAiGeneration;
import org.hongtu.saas.mapper.SaasAiGenerationMapper;
import org.hongtu.saas.service.SaasAiGenerationService;
import org.hongtu.saas.service.SaasEntitlementService;
import org.hongtu.saas.view.EntitlementDecision;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证 AI 请求预占与失败退款不会因重试重复执行。 */
class SaasAiGenerationServiceTest {
    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "ai-generation-tests"),
                SaasAiGeneration.class);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void sameRequestOnlyConsumesOnce() {
        TenantContext.setTenantId(10L);
        SaasAiGenerationMapper mapper = mock(SaasAiGenerationMapper.class);
        SaasEntitlementService entitlements = mock(SaasEntitlementService.class);
        SaasAiGenerationService service = new SaasAiGenerationService();
        ReflectionTestUtils.setField(service, "generations", mapper);
        ReflectionTestUtils.setField(service, "entitlements", entitlements);
        String id = UUID.randomUUID().toString();
        AiGenerationDTO request = new AiGenerationDTO().setRequestId(id)
                .setRequestHash("a".repeat(64)).setOperation("BRIEFING");
        when(mapper.selectOne(any())).thenReturn(null, new SaasAiGeneration()
                .setTenantId(10L).setRequestId(id).setRequestHash(request.getRequestHash())
                .setOperation("BRIEFING").setStatus("PROCESSING"));
        when(entitlements.hasFeature(10L, SaasFeatureCode.AI_MANAGER)).thenReturn(true);
        when(entitlements.consume(eq(10L), eq(SaasFeatureCode.AI_MANAGER_CALLS), eq(BigDecimal.ONE), anyString()))
                .thenReturn(new EntitlementDecision().setAllowed(true));

        assertTrue(service.reserve(request).isAcquired());
        assertFalse(service.reserve(request).isAcquired());
        verify(entitlements, times(1)).consume(10L, SaasFeatureCode.AI_MANAGER_CALLS,
                BigDecimal.ONE, "ai-call:" + id);
    }

    @Test
    void failureRefundsReservationOnce() {
        TenantContext.setTenantId(10L);
        SaasAiGenerationMapper mapper = mock(SaasAiGenerationMapper.class);
        SaasEntitlementService entitlements = mock(SaasEntitlementService.class);
        SaasAiGenerationService service = new SaasAiGenerationService();
        ReflectionTestUtils.setField(service, "generations", mapper);
        ReflectionTestUtils.setField(service, "entitlements", entitlements);
        String id = UUID.randomUUID().toString();
        when(mapper.selectOne(any())).thenReturn(new SaasAiGeneration()
                .setTenantId(10L).setRequestId(id).setStatus("PROCESSING"),
                new SaasAiGeneration().setTenantId(10L).setRequestId(id).setStatus("FAILED"));

        assertEquals("FAILED", service.fail(new AiGenerationDTO().setRequestId(id)).getStatus());
        assertEquals("FAILED", service.fail(new AiGenerationDTO().setRequestId(id)).getStatus());
        verify(entitlements, times(1)).refundConsumption(10L, "ai-call:" + id, "ai-refund:" + id);
    }
}
