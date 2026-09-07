package org.hongtu;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DAO.TableInfoMapper;
import org.hongtu.restaurant.DTO.TableInfo;
import org.hongtu.restaurant.DTO.TableReservationRequest;
import org.hongtu.restaurant.enums.TableStatusEnum;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.impl.TableReservationServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TableReservationServiceTest {
    @BeforeAll static void metadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "reservation-tests"),
                org.hongtu.restaurant.DTO.TableReservation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "reservation-table-tests"),
                TableInfo.class);
    }

    @Test void createsIndependentTimeSlotWithoutChangingTableStatus() {
        Fixture f = fixture();
        doReturn(0L).when(f.service).count(any());
        doReturn(true).when(f.service).save(any());

        var result = f.service.createReservation(validRequest(), 88L);

        assertEquals(9L, result.getTableId());
        assertEquals(0, result.getStatus());
        assertEquals("待到店", result.getStatusDesc());
        assertEquals(TableStatusEnum.IDLE, f.table.getStatus());
        verify(f.service).save(any());
        verify(f.scope).checkStoreAccess(7L);
    }

    @Test void rejectsOverlappingActiveTimeSlot() {
        Fixture f = fixture();
        doReturn(1L).when(f.service).count(any());
        assertThrows(BusinessException.class, () -> f.service.createReservation(validRequest(), 88L));
        verify(f.service, never()).save(any());
    }

    @Test void validatesEndTimeAndPhone() {
        Fixture f = fixture();
        var invalidTime = validRequest();
        invalidTime.setExpectedEndTime(invalidTime.getExpectedArrivalTime());
        assertThrows(BusinessException.class, () -> f.service.createReservation(invalidTime, 88L));
        var invalidPhone = validRequest();
        invalidPhone.setReservationPhone("123");
        assertThrows(BusinessException.class, () -> f.service.createReservation(invalidPhone, 88L));
    }

    private static TableReservationRequest validRequest() {
        var request = new TableReservationRequest();
        request.setTableId(9L);
        request.setReservationPhone("13800138000");
        request.setPeopleCount(4);
        request.setExpectedArrivalTime(LocalDateTime.now().plusHours(1));
        request.setExpectedEndTime(LocalDateTime.now().plusHours(3));
        request.setReservationRemark("靠窗");
        return request;
    }

    private static Fixture fixture() {
        var service = spy(new TableReservationServiceImpl());
        var tableMapper = mock(TableInfoMapper.class);
        var storeMapper = mock(StoreInfoMapper.class);
        var scope = mock(StoreDataScopeService.class);
        var table = new TableInfo().setId(9L).setStoreId(7L).setStatus(TableStatusEnum.IDLE)
                .setTableNumber("A01").setTableName("窗边桌");
        when(tableMapper.selectOne(any())).thenReturn(table);
        ReflectionTestUtils.setField(service, "tableInfoMapper", tableMapper);
        ReflectionTestUtils.setField(service, "storeInfoMapper", storeMapper);
        ReflectionTestUtils.setField(service, "storeDataScopeService", scope);
        return new Fixture(service, scope, table);
    }

    private record Fixture(TableReservationServiceImpl service, StoreDataScopeService scope, TableInfo table) {}
}
