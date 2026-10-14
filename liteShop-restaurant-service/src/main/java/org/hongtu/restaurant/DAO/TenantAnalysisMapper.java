package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 租户经营分析汇总数据访问接口。
 */
@Mapper
public interface TenantAnalysisMapper {

    Map<String, Object> selectOverview(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("previousStartDate") LocalDate previousStartDate,
                                       @Param("previousEndDate") LocalDate previousEndDate,
                                       @Param("groupId") Long groupId);

    List<Map<String, Object>> selectStoreMetrics(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate,
                                                  @Param("previousStartDate") LocalDate previousStartDate,
                                                  @Param("previousEndDate") LocalDate previousEndDate,
                                                  @Param("groupId") Long groupId);

    List<Map<String, Object>> selectDishMetrics(@Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate,
                                                 @Param("groupId") Long groupId);

    List<Map<String, Object>> selectSlowMovingDishMetrics(@Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate,
                                                           @Param("groupId") Long groupId,
                                                           @Param("limit") Integer limit);

    Integer countMatrixStores(@Param("groupId") Long groupId);

    List<Map<String, Object>> selectMatrixStores(@Param("groupId") Long groupId,
                                                  @Param("limit") Integer limit);

    List<Map<String, Object>> selectMatrixCells(@Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate,
                                                 @Param("groupId") Long groupId,
                                                 @Param("dishIds") List<Long> dishIds);

    List<Map<String, Object>> selectComparisonDishes(@Param("groupId") Long groupId);

    List<Map<String, Object>> selectDishStoreComparison(@Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate,
                                                         @Param("groupId") Long groupId,
                                                         @Param("dishId") Long dishId);
}
