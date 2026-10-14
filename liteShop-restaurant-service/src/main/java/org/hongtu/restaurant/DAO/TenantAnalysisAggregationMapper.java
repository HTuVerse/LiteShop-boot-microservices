package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 租户经营分析日汇总刷新数据访问接口。
 */
@Mapper
public interface TenantAnalysisAggregationMapper {

    List<Map<String, Object>> selectDirtyBatchForUpdate(@Param("limit") Integer limit);

    void deleteStoreDaily(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                          @Param("bizDate") LocalDate bizDate);

    void insertStoreDaily(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                          @Param("bizDate") LocalDate bizDate);

    void deleteStoreDishDaily(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                              @Param("bizDate") LocalDate bizDate);

    void insertStoreDishDaily(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                              @Param("bizDate") LocalDate bizDate);

    void deleteDirtyKey(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                        @Param("bizDate") LocalDate bizDate);
}
