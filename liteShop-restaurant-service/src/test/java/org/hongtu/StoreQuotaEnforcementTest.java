package org.hongtu;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.service.StoreDataScopeService;
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
    void failedStoreInsertCompensatesReservedQuota() {
        Fixture fixture = fixture();
        when(fixture.mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.user.consumeEntitlement(any())).thenReturn(allowed());
        when(fixture.user.releaseEntitlement(any())).thenReturn(allowed());
        doReturn(false).when(fixture.service).save(any(StoreInfo.class));

        assertThrows(BusinessException.class,
                () -> fixture.service.addStore(new StoreInfo().setStoreCode("S-002")));

        verify(fixture.user).releaseEntitlement(argThat(request ->
                request.getIdempotencyKey().startsWith("store:create:rollback:")));
    }

    @Test
    void deletingStoreReleasesOneQuota() {
        Fixture fixture = fixture();
        doReturn(new StoreInfo().setId(9L)).when(fixture.service).getById(9L);
        doReturn(true).when(fixture.service).removeById(9L);
        when(fixture.user.releaseEntitlement(any())).thenReturn(allowed());

        assertTrue(fixture.service.deleteStore(9L));

        verify(fixture.user).releaseEntitlement(argThat(request ->
                "store:delete:9".equals(request.getIdempotencyKey())
                        && java.math.BigDecimal.ONE.compareTo(request.getAmount()) == 0));
    }

    private static Result<EntitlementDecisionDTO> allowed() {
        return Result.success(new EntitlementDecisionDTO().setAllowed(true).setFeatureCode("store_count"));
    }

    private static Fixture fixture() {
        StoreInfoServiceImpl service = spy(new StoreInfoServiceImpl());
        StoreInfoMapper mapper = mock(StoreInfoMapper.class);
        StoreDataScopeService scope = mock(StoreDataScopeService.class);
        UserFeignClient user = mock(UserFeignClient.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "storeInfoMapper", mapper);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scope);
        ReflectionTestUtils.setField(service, "userFeignClient", user);
        return new Fixture(service, mapper, user);
    }

    private record Fixture(StoreInfoServiceImpl service, StoreInfoMapper mapper, UserFeignClient user) {}
}
