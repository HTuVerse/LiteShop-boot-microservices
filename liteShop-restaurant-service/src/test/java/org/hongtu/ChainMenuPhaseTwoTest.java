package org.hongtu;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.hongtu.restaurant.DAO.MenuChangeRecordMapper;
import org.hongtu.restaurant.DTO.MenuChangeRecord;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.StoreMenuPolicy;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.VO.MenuChangeSubmitVO;
import org.hongtu.restaurant.VO.StoreDishConfigRequest;
import org.hongtu.restaurant.enums.MenuChangeStatusEnum;
import org.hongtu.restaurant.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 连锁菜单第二阶段的审批、定时生效和审计状态测试。
 */
class ChainMenuPhaseTwoTest {

    @BeforeAll
    static void metadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "chain-menu-phase-two-tests");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MenuChangeRecord.class);
    }

    @Test
    void restrictedStoreChangeWaitsForApprovalWhenPolicyRequiresIt() {
        Fixture fixture = fixture(true, true);

        MenuChangeSubmitVO result = fixture.service.submit(request(null), 88L);

        assertEquals(MenuChangeStatusEnum.PENDING.getCode(), result.getRecord().getStatus());
        verify(fixture.dishService, never()).updateStoreDishConfig(any());
    }

    @Test
    void headquarterFutureChangeIsScheduledWithoutImmediateMutation() {
        Fixture fixture = fixture(false, false);

        MenuChangeSubmitVO result = fixture.service.submit(request(LocalDateTime.now().plusHours(2)), 99L);

        assertEquals(MenuChangeStatusEnum.SCHEDULED.getCode(), result.getRecord().getStatus());
        verify(fixture.dishService, never()).updateStoreDishConfig(any());
    }

    @Test
    void headquarterImmediateChangeIsAppliedAndAudited() {
        Fixture fixture = fixture(false, false);

        MenuChangeSubmitVO result = fixture.service.submit(request(null), 99L);

        assertEquals(MenuChangeStatusEnum.APPLIED.getCode(), result.getRecord().getStatus());
        assertEquals(new BigDecimal("118.00"), result.getEffectiveDish().getEffectiveSalePrice());
        verify(fixture.dishService).updateStoreDishConfig(any(StoreDishConfigRequest.class));
        verify(fixture.recordMapper, times(2)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void headquarterApprovalAppliesPendingStoreChange() {
        Fixture fixture = fixture(true, true);
        MenuChangeSubmitVO pending = fixture.service.submit(request(null), 88L);
        when(fixture.recordMapper.selectById(pending.getRecord().getId())).thenReturn(pending.getRecord());
        when(fixture.scopeService.requiresStoreRestriction()).thenReturn(false);

        MenuChangeSubmitVO approved = fixture.service.approve(pending.getRecord().getId(), 99L, "同意执行");

        assertEquals(MenuChangeStatusEnum.APPLIED.getCode(), approved.getRecord().getStatus());
        verify(fixture.dishService).updateStoreDishConfig(any(StoreDishConfigRequest.class));
    }

    private Fixture fixture(boolean restricted, boolean requireApproval) {
        MenuChangeRecordMapper recordMapper = mock(MenuChangeRecordMapper.class);
        DishService dishService = mock(DishService.class);
        StoreMenuPolicyService policyService = mock(StoreMenuPolicyService.class);
        StoreDataScopeService scopeService = mock(StoreDataScopeService.class);
        StoreInfoService storeInfoService = mock(StoreInfoService.class);
        StoreGroupService groupService = mock(StoreGroupService.class);
        DishVO before = new DishVO();
        before.setId(100L);
        before.setStoreId(10L);
        before.setDishName("总部菜品");
        before.setSalePrice(new BigDecimal("100.00"));
        DishVO after = new DishVO();
        after.setId(100L);
        after.setStoreId(10L);
        after.setSalePrice(new BigDecimal("118.00"));
        after.setEffectiveSalePrice(new BigDecimal("118.00"));

        when(scopeService.requiresStoreRestriction()).thenReturn(restricted);
        when(storeInfoService.getById(10L)).thenReturn(new StoreInfo().setId(10L));
        when(dishService.getEffectiveDish(100L, 10L, false)).thenReturn(before);
        when(dishService.updateStoreDishConfig(any())).thenReturn(after);
        when(policyService.getEffectivePolicy(10L)).thenReturn(new StoreMenuPolicy()
                .setStoreId(10L).setRequireApproval(requireApproval ? 1 : 0));
        when(recordMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(recordMapper.insert(any(MenuChangeRecord.class))).thenAnswer(invocation -> {
            MenuChangeRecord record = invocation.getArgument(0);
            record.setId(900L).setTenantId(1L);
            return 1;
        });
        when(recordMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        MenuChangeService service = new MenuChangeService();
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "dishService", dishService);
        ReflectionTestUtils.setField(service, "policyService", policyService);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scopeService);
        ReflectionTestUtils.setField(service, "storeInfoService", storeInfoService);
        ReflectionTestUtils.setField(service, "storeGroupService", groupService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return new Fixture(service, recordMapper, dishService, scopeService);
    }

    private StoreDishConfigRequest request(LocalDateTime effectiveAt) {
        StoreDishConfigRequest request = new StoreDishConfigRequest();
        request.setStoreId(10L);
        request.setDishId(100L);
        request.setSalePriceOverride(new BigDecimal("118.00"));
        request.setListingStatus(1);
        request.setSoldOut(0);
        request.setInventoryMode(1);
        request.setStock(20);
        request.setEffectiveAt(effectiveAt);
        request.setChangeReason("区域价格调整");
        return request;
    }

    private record Fixture(MenuChangeService service, MenuChangeRecordMapper recordMapper,
                           DishService dishService, StoreDataScopeService scopeService) {
    }
}
