package org.hongtu;
import org.hongtu.restaurant.service.impl.TableInfoServiceImpl;
import org.hongtu.restaurant.service.*;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.enums.TableStatusEnum;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TableStatusTransitionTest {
    @org.junit.jupiter.api.BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
            new com.baomidou.mybatisplus.core.MybatisConfiguration(), "regression-tests"), org.hongtu.restaurant.DTO.TableInfo.class);
    }
    @Test void onlyIdleAndDisabledCanBeSetManuallyAndActiveSessionBlocksChanges(){
        var service=spy(new TableInfoServiceImpl());var sessions=mock(TableSessionService.class);var scope=mock(StoreDataScopeService.class);
        ReflectionTestUtils.setField(service,"tableSessionService",sessions);ReflectionTestUtils.setField(service,"storeDataScopeService",scope);
        var table=new TableInfo();table.setId(9L);table.setStoreId(7L);table.setStatus(TableStatusEnum.IDLE);
        doReturn(table).when(service).getOne(any());doReturn(true).when(service).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertTrue(service.updateTableStatus(9L,0));verify(scope).checkStoreAccess(7L);
        assertThrows(BusinessException.class,()->service.updateTableStatus(9L,2));
        when(sessions.getActiveSessionByTableId(9L)).thenReturn(new TableSession());
        assertThrows(BusinessException.class,()->service.updateTableStatus(9L,3));
        assertThrows(BusinessException.class,()->service.updateTableStatus(9L,1));
        assertThrows(BusinessException.class,()->service.updateTableStatus(9L,99));
        verify(service,times(1)).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
