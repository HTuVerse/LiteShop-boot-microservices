package org.hongtu;

import org.hongtu.restaurant.DAO.TenantAnalysisMapper;
import org.hongtu.restaurant.VO.TenantAnalysisQuery;
import org.hongtu.restaurant.VO.analysis.TenantAnalysisVO;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.StoreGroupService;
import org.hongtu.restaurant.service.TenantAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 租户级经营分析计算口径测试。 */
class TenantAnalysisServiceTest {

    private TenantAnalysisMapper mapper;
    private TenantAnalysisService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TenantAnalysisMapper.class);
        StoreDataScopeService dataScopeService = mock(StoreDataScopeService.class);
        when(dataScopeService.requiresStoreRestriction()).thenReturn(false);
        service = new TenantAnalysisService(mapper, dataScopeService, mock(StoreGroupService.class));
    }

    @Test
    void totalAndProfitRankingsKeepIndependentRankNumbers() {
        when(mapper.selectDishMetrics(any(LocalDate.class), any(LocalDate.class), isNull()))
                .thenReturn(List.of(dish(1L, "销量冠军", 100, "10.00"),
                        dish(2L, "毛利冠军", 50, "100.00")));
        when(mapper.selectSlowMovingDishMetrics(any(LocalDate.class), any(LocalDate.class), isNull(), anyInt()))
                .thenReturn(List.of());

        TenantAnalysisQuery query = new TenantAnalysisQuery();
        query.setMetric("QUANTITY");
        TenantAnalysisVO.DishRanking result = service.dishRanking(query);

        assertEquals("销量冠军", result.getRanking().getFirst().getDishName());
        assertEquals(1, result.getRanking().getFirst().getRanking());
        assertEquals(2, result.getRanking().getLast().getRanking());
        assertEquals("毛利冠军", result.getHighProfit().getFirst().getDishName());
        assertEquals(1, result.getHighProfit().getFirst().getRanking());
    }

    @Test
    void costCompletenessIncludesPaidAndGiftQuantities() {
        Map<String, Object> row = dish(1L, "赠品菜", 2, "20.00");
        row.put("giftQuantity", 2);
        row.put("unknownCostQuantity", 2);
        when(mapper.selectDishMetrics(any(LocalDate.class), any(LocalDate.class), isNull()))
                .thenReturn(List.of(row));
        when(mapper.selectSlowMovingDishMetrics(any(LocalDate.class), any(LocalDate.class), isNull(), anyInt()))
                .thenReturn(List.of());

        TenantAnalysisVO.DishRanking result = service.dishRanking(new TenantAnalysisQuery());

        assertEquals(new BigDecimal("50.00"), result.getRanking().getFirst().getCostCompletenessRate());
    }

    @Test
    void selectedDishComparisonCalculatesStoreSalesShareAndRanking() {
        Map<String, Object> option = new HashMap<>();
        option.put("dishId", 9L);
        option.put("dishName", "酸菜鱼");
        option.put("categoryName", "招牌菜");
        when(mapper.selectComparisonDishes(isNull())).thenReturn(List.of(option));

        Map<String, Object> firstStore = comparisonStore(1L, "一店", 12, "240.00", "1200.00");
        Map<String, Object> secondStore = comparisonStore(2L, "二店", 5, "80.00", "800.00");
        when(mapper.selectDishStoreComparison(any(LocalDate.class), any(LocalDate.class), isNull(), any()))
                .thenReturn(List.of(secondStore, firstStore));

        TenantAnalysisQuery query = new TenantAnalysisQuery();
        query.setDishId(9L);
        TenantAnalysisVO.StoreDishComparison result = service.storeDishComparison(query);

        assertEquals("酸菜鱼", result.getSelectedDishName());
        assertEquals("一店", result.getStores().getFirst().getStoreName());
        assertEquals(1, result.getStores().getFirst().getRanking());
        assertEquals(new BigDecimal("20.00"), result.getStores().getFirst().getSalesShareRate());
        assertEquals(new BigDecimal("10.00"), result.getStores().getLast().getSalesShareRate());
    }

    private Map<String, Object> dish(Long id, String name, int quantity, String profit) {
        Map<String, Object> row = new HashMap<>();
        row.put("dishId", id);
        row.put("dishName", name);
        row.put("categoryName", "测试分类");
        row.put("salesQuantity", quantity);
        row.put("grossSales", new BigDecimal("200.00"));
        row.put("netSales", new BigDecimal("180.00"));
        row.put("costAmount", new BigDecimal("80.00"));
        row.put("grossProfit", new BigDecimal(profit));
        row.put("orderCount", 5);
        row.put("giftQuantity", 0);
        row.put("unknownCostQuantity", 0);
        return row;
    }

    private Map<String, Object> comparisonStore(Long id, String name, int quantity,
                                                 String netSales, String storeNetSales) {
        Map<String, Object> row = new HashMap<>();
        row.put("storeId", id);
        row.put("storeName", name);
        row.put("listed", 1);
        row.put("salesQuantity", quantity);
        row.put("netSales", new BigDecimal(netSales));
        row.put("storeNetSales", new BigDecimal(storeNetSales));
        return row;
    }
}
