package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 租户经营分析接口返回对象集合。
 */
public final class TenantAnalysisVO {

    private TenantAnalysisVO() {
    }

    /** 当前周期及对比周期。 */
    @Data
    @Accessors(chain = true)
    public static class Period {
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate previousStartDate;
        private LocalDate previousEndDate;
        private String comparisonLabel;
    }

    /** 单项经营指标。 */
    @Data
    @Accessors(chain = true)
    public static class MetricComparison {
        private BigDecimal currentValue = BigDecimal.ZERO;
        private BigDecimal previousValue = BigDecimal.ZERO;
        private BigDecimal changeAmount = BigDecimal.ZERO;
        /** 增长率百分比；上期为零且本期大于零时为空。 */
        private BigDecimal growthRate;
        /** NORMAL、NEW、UNCHANGED。 */
        private String growthStatus;
    }

    /** 核心经营总览。 */
    @Data
    @Accessors(chain = true)
    public static class Overview {
        private Period period;
        private Integer selectedStoreCount = 0;
        private MetricComparison grossSales;
        private MetricComparison netSales;
        private MetricComparison orderCount;
        private MetricComparison avgOrderAmount;
        private MetricComparison avgStoreSales;
        private LocalDateTime dataUpdatedAt;
    }

    /** 门店排行单项。 */
    @Data
    @Accessors(chain = true)
    public static class StoreRankItem {
        private Integer ranking;
        private Long storeId;
        private String storeName;
        private BigDecimal currentValue = BigDecimal.ZERO;
        private BigDecimal previousValue = BigDecimal.ZERO;
        private BigDecimal changeAmount = BigDecimal.ZERO;
        private BigDecimal growthRate;
        private String growthStatus;
        private BigDecimal grossSales = BigDecimal.ZERO;
        private BigDecimal netSales = BigDecimal.ZERO;
        private Long orderCount = 0L;
        private BigDecimal avgOrderAmount = BigDecimal.ZERO;
    }

    /** 门店销售及增长榜。 */
    @Data
    @Accessors(chain = true)
    public static class StoreRanking {
        private String metric;
        private Period period;
        private List<StoreRankItem> salesRanking = new ArrayList<>();
        private List<StoreRankItem> growthTop = new ArrayList<>();
        private List<StoreRankItem> growthBottom = new ArrayList<>();
    }

    /** 菜品排行单项。 */
    @Data
    @Accessors(chain = true)
    public static class DishRankItem {
        private Integer ranking;
        private Long dishId;
        private String dishName;
        private String categoryName;
        private Integer salesQuantity = 0;
        private BigDecimal grossSales = BigDecimal.ZERO;
        private BigDecimal netSales = BigDecimal.ZERO;
        private BigDecimal costAmount;
        private BigDecimal grossProfit;
        private BigDecimal grossMarginRate;
        private Long orderCount = 0L;
        private Integer giftQuantity = 0;
        private Integer unknownCostQuantity = 0;
        private BigDecimal costCompletenessRate = BigDecimal.ZERO;
        private LocalDate lastSaleDate;
        private Integer inactiveDays;
    }

    /** 菜品销量、销售额、毛利和滞销榜。 */
    @Data
    @Accessors(chain = true)
    public static class DishRanking {
        private String metric;
        private Period period;
        private List<DishRankItem> ranking = new ArrayList<>();
        private List<DishRankItem> highProfit = new ArrayList<>();
        private List<DishRankItem> slowMoving = new ArrayList<>();
    }

    /** 矩阵中的门店列。 */
    @Data
    @Accessors(chain = true)
    public static class MatrixStore {
        private Long storeId;
        private String storeName;
    }

    /** 门店菜品指标单元格。 */
    @Data
    @Accessors(chain = true)
    public static class MatrixCell {
        private Long storeId;
        private Boolean listed;
        private Integer salesQuantity = 0;
        private BigDecimal netSales = BigDecimal.ZERO;
        private BigDecimal grossProfit;
        private BigDecimal orderPenetrationRate = BigDecimal.ZERO;
        private BigDecimal performanceIndex;
    }

    /** 矩阵中的菜品行。 */
    @Data
    @Accessors(chain = true)
    public static class MatrixRow {
        private Long dishId;
        private String dishName;
        private String categoryName;
        private List<MatrixCell> cells = new ArrayList<>();
    }

    /** 门店与菜品横向比较矩阵。 */
    @Data
    @Accessors(chain = true)
    public static class StoreDishMatrix {
        private Period period;
        private Integer currentPage;
        private Integer pageSize;
        private Integer total;
        private Integer totalStoreCount;
        private List<MatrixStore> stores = new ArrayList<>();
        private List<MatrixRow> rows = new ArrayList<>();
    }

    /** 门店横向比较的菜品选项。 */
    @Data
    @Accessors(chain = true)
    public static class ComparisonDish {
        private Long dishId;
        private String dishName;
        private String categoryName;
    }

    /** 单个菜品在一家门店的经营表现。 */
    @Data
    @Accessors(chain = true)
    public static class StoreDishComparisonItem {
        private Integer ranking;
        private Long storeId;
        private String storeName;
        private Boolean listed;
        private Integer salesQuantity = 0;
        private BigDecimal netSales = BigDecimal.ZERO;
        private BigDecimal storeNetSales = BigDecimal.ZERO;
        private BigDecimal salesShareRate = BigDecimal.ZERO;
    }

    /** 选定菜品后按门店展开的横向经营比较。 */
    @Data
    @Accessors(chain = true)
    public static class StoreDishComparison {
        private Period period;
        private Long selectedDishId;
        private String selectedDishName;
        private String selectedCategoryName;
        private List<ComparisonDish> dishes = new ArrayList<>();
        private List<StoreDishComparisonItem> stores = new ArrayList<>();
    }
}
