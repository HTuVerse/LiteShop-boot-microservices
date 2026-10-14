package org.hongtu;

import org.hongtu.restaurant.DAO.RestaurantAnalysisMapper;
import org.hongtu.restaurant.VO.analysis.DishSalesRankingVO;
import org.hongtu.restaurant.VO.analysis.DishSalesTrendVO;
import org.hongtu.restaurant.VO.analysis.SalesOverviewVO;
import org.hongtu.restaurant.VO.analysis.SlowMovingDishVO;
import org.hongtu.restaurant.service.impl.RestaurantAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 经营分析核心口径测试。
 */
class RestaurantAnalysisServiceImplTest {

    private RestaurantAnalysisMapper mapper;
    private RestaurantAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RestaurantAnalysisMapper.class);
        service = new RestaurantAnalysisServiceImpl();
        ReflectionTestUtils.setField(service, "analysisMapper", mapper);
    }

    @Test
    void zeroBaselineGrowthIsReportedAsNewInsteadOfFakeOneHundredPercent() {
        Map<String, Object> current = salesData("120.00", 6, "20.00");
        Map<String, Object> empty = salesData("0", 0, "0");
        when(mapper.getSalesStatistics(eq(1L), anyString(), anyString()))
                .thenReturn(current, empty, empty);

        SalesOverviewVO result = service.getSalesOverview(1L, 7, null, null);

        assertEquals(new BigDecimal("120.00"), result.getCurrentSales());
        assertNull(result.getPeriodOnPeriodGrowthRate());
        assertNull(result.getYearOnYearGrowthRate());
    }

    @Test
    void dishPerformanceUsesPaidQuantityAndOrderPenetration() {
        Map<String, Object> dish = new HashMap<>();
        dish.put("dishId", 9L);
        dish.put("dishName", "招牌菜");
        dish.put("salesQuantity", 10);
        dish.put("salesAmount", new BigDecimal("280.00"));
        dish.put("orderCount", 8);
        dish.put("giftQuantity", 2);
        dish.put("stock", 20);
        when(mapper.getDishSalesRanking(eq(1L), anyString(), anyString(), eq(10)))
                .thenReturn(List.of(dish));
        when(mapper.getSalesStatistics(eq(1L), anyString(), anyString()))
                .thenReturn(salesData("1000.00", 20, "50.00"));

        List<DishSalesRankingVO> result = service.getTopSellingDishes(1L, 30, 10, null, null);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("40.00"), result.getFirst().getOrderPenetrationRate());
        assertEquals(new BigDecimal("28.00"), result.getFirst().getAvgSellingPrice());
        assertEquals(2, result.getFirst().getGiftQuantity());
    }

    @Test
    void slowMovingListRequiresEnoughObservationAndSevenInactiveDays() {
        Map<String, Object> inactiveDish = new HashMap<>();
        inactiveDish.put("dishId", 11L);
        inactiveDish.put("dishName", "待观察菜品");
        inactiveDish.put("salesLast30Days", 0);
        inactiveDish.put("salesAmountLast30Days", BigDecimal.ZERO);
        inactiveDish.put("stock", 12);
        inactiveDish.put("createdAt", LocalDateTime.now().minusDays(40));
        when(mapper.getSlowMovingDishes(eq(1L), anyString(), anyString()))
                .thenReturn(List.of(inactiveDish));

        List<SlowMovingDishVO> result = service.getSlowMovingDishes(1L, 30, null, null);

        assertEquals(1, result.size());
        assertEquals(0.0, result.getFirst().getAvgDailySales());
        assertEquals("库存积压，建议核查品质、定价或下架", result.getFirst().getSuggestion());
    }

    @Test
    void dishTrendKeepsMissingDatesVisibleAsZero() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        DishSalesTrendVO sold = new DishSalesTrendVO();
        sold.setDate(yesterday);
        sold.setSalesQuantity(3);
        sold.setSalesAmount(new BigDecimal("66.00"));
        when(mapper.getDishSalesTrendByDishId(eq(1L), eq(9L), anyString(), anyString()))
                .thenReturn(List.of(sold));

        List<DishSalesTrendVO> result = service.getDishSalesTrend(1L, 9L, 3, null, null);

        assertEquals(3, result.size());
        assertEquals(0, result.getFirst().getSalesQuantity());
        assertEquals(3, result.get(1).getSalesQuantity());
        assertEquals(BigDecimal.ZERO, result.getLast().getSalesAmount());
    }

    private Map<String, Object> salesData(String sales, int orders, String average) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalSales", new BigDecimal(sales));
        data.put("orderCount", orders);
        data.put("avgOrderAmount", new BigDecimal(average));
        return data;
    }
}
