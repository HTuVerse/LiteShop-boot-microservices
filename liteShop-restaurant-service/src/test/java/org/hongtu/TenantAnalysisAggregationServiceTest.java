package org.hongtu;

import org.hongtu.restaurant.DAO.TenantAnalysisAggregationMapper;
import org.hongtu.restaurant.service.TenantAnalysisAggregationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 增量汇总队列消费测试。 */
class TenantAnalysisAggregationServiceTest {

    @Test
    void rebuildsOnlyTheDirtyTenantStoreDateAndAcknowledgesItLast() {
        TenantAnalysisAggregationMapper mapper = mock(TenantAnalysisAggregationMapper.class);
        TenantAnalysisAggregationService service = new TenantAnalysisAggregationService(mapper);
        LocalDate bizDate = LocalDate.of(2026, 9, 15);
        when(mapper.selectDirtyBatchForUpdate(100)).thenReturn(List.of(Map.of(
                "tenantId", 11L, "storeId", 22L, "bizDate", bizDate)));

        int count = service.rebuildDirtyBatch(100);

        assertEquals(1, count);
        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteStoreDishDaily(11L, 22L, bizDate);
        order.verify(mapper).deleteStoreDaily(11L, 22L, bizDate);
        order.verify(mapper).insertStoreDaily(11L, 22L, bizDate);
        order.verify(mapper).insertStoreDishDaily(11L, 22L, bizDate);
        order.verify(mapper).deleteDirtyKey(11L, 22L, bizDate);
        verify(mapper).selectDirtyBatchForUpdate(100);
    }
}
