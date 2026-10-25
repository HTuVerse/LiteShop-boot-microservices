package org.hongtu;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.LocalOutboxService;
import org.hongtu.restaurant.service.impl.StoreInfoServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StoreQuotaEnforcementTest {
    @BeforeAll
    static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "store-quota-tests"),
                StoreInfo.class);
    }

    @Test
    void quotaDenialStopsStoreCreationAndKeepsSharedErrorCode() {
        Fixture fixture = fixture();
        when(fixture.mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.user.consumeEntitlement(any())).thenReturn(Result.success(new EntitlementDecisionDTO()
                .setAllowed(false).setFeatureCode("store_count").setLimit(java.math.BigDecimal.valueOf(5))
                .setUsed(java.math.BigDecimal.valueOf(5)).setReason("当前套餐最多支持 5 家门店")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.addStore(new StoreInfo().setStoreCode("S-001")));

        assertEquals(SaasErrorCode.QUOTA_EXCEEDED, error.getCode());
        assertTrue(error.getMessage().contains("5 家门店"));
        verify(fixture.service, never()).save(any(StoreInfo.class));
        verify(fixture.user, never()).releaseEntitlement(any());
    }

    @Test
    void technicalQuotaReasonIsConvertedToActionableMessage() {
        Fixture fixture = fixture();
        when(fixture.mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.user.consumeEntitlement(any())).thenReturn(Result.success(new EntitlementDecisionDTO()
                .setAllowed(false).setFeatureCode("store_count").setReason("QUOTA_EXCEEDED")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.addStore(new StoreInfo().setStoreCode("S-QUOTA")));

        assertEquals(SaasErrorCode.QUOTA_EXCEEDED, error.getCode());
        assertEquals("门店数量已达到当前套餐上限，请升级套餐或购买额外门店", error.getMessage());
    }

    @Test
    void disabledStoreEntitlementKeepsFeatureDisabledErrorCode() {
        Fixture fixture = fixture();
        when(fixture.mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.user.consumeEntitlement(any())).thenReturn(Result.success(new EntitlementDecisionDTO()
                .setAllowed(false).setFeatureCode("store_count").setReason("FEATURE_DISABLED")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.addStore(new StoreInfo().setStoreCode("S-NO-PLAN")));

        assertEquals(SaasErrorCode.FEATURE_DISABLED, error.getCode());
        assertEquals("当前套餐未开通门店额度，请先分配已发布的套餐版本", error.getMessage());
    }

    @Test
    void failedStoreInsertLeavesReservationForDurableCompensation() {
        Fixture fixture = fixture();
        when(fixture.mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.user.consumeEntitlement(any())).thenReturn(allowed());
        doReturn(false).when(fixture.service).save(any(StoreInfo.class));

        assertThrows(BusinessException.class,
                () -> fixture.service.addStore(new StoreInfo().setStoreCode("S-002")));

        verify(fixture.outbox).executeRemote(eq("STORE_QUOTA_RESERVE"),
                startsWith("store:create:"), anyString(), any(Runnable.class));
        verify(fixture.user, never()).releaseEntitlement(any());
    }

    @Test
    void deletingUnusedStoreQueuesQuotaReleaseAfterLocalDelete() {
        Fixture fixture = fixture();
        doReturn(java.util.List.of(new StoreInfo().setId(9L))).when(fixture.service).listByIds(anyCollection());
        when(fixture.mapper.deleteStores(anyLong(), anyList())).thenReturn(1);

        assertTrue(fixture.service.deleteStore(9L));

        verify(fixture.outbox).enqueueStoreQuotaRelease(9L);
        verify(fixture.user, never()).releaseEntitlement(any());
    }

    private static Result<EntitlementDecisionDTO> allowed() {
        return Result.success(new EntitlementDecisionDTO().setAllowed(true).setFeatureCode("store_count"));
    }

    private static Fixture fixture() {
        StoreInfoServiceImpl service = spy(new StoreInfoServiceImpl());
        StoreInfoMapper mapper = mock(StoreInfoMapper.class);
        StoreDataScopeService scope = mock(StoreDataScopeService.class);
        UserFeignClient user = mock(UserFeignClient.class);
        LocalOutboxService outbox = mock(LocalOutboxService.class);
        ResourceBindingCoordinator resources = mock(ResourceBindingCoordinator.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "storeInfoMapper", mapper);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scope);
        ReflectionTestUtils.setField(service, "userFeignClient", user);
        ReflectionTestUtils.setField(service, "localOutboxService", outbox);
        ReflectionTestUtils.setField(service, "resourceBindings", resources);
        when(user.ensureStoreCountBaseline(anyLong())).thenReturn(Result.success());
        doAnswer(call -> { call.<Runnable>getArgument(3).run(); return null; })
                .when(outbox).executeRemote(anyString(), anyString(), anyString(), any(Runnable.class));
        return new Fixture(service, mapper, user, outbox);
    }

    private record Fixture(StoreInfoServiceImpl service, StoreInfoMapper mapper,
                           UserFeignClient user, LocalOutboxService outbox) {}
}
