package org.hongtu.restaurant.service;

import org.hongtu.restaurant.DAO.TenantAnalysisAggregationMapper;
import org.hongtu.tenant.TenantIgnore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 租户经营分析日汇总刷新服务。
 */
@Service
public class TenantAnalysisAggregationService {

    private final TenantAnalysisAggregationMapper mapper;

    public TenantAnalysisAggregationService(TenantAnalysisAggregationMapper mapper) {
        this.mapper = mapper;
    }

    /** 从持久化脏数据队列取一批门店营业日，并在同一事务内完成重算和确认。 */
    @Transactional(rollbackFor = Exception.class)
    public int rebuildDirtyBatch(int requestedBatchSize) {
        int batchSize = Math.min(200, Math.max(1, requestedBatchSize));
        return TenantIgnore.get(() -> {
            List<Map<String, Object>> dirtyKeys = mapper.selectDirtyBatchForUpdate(batchSize);
            for (Map<String, Object> key : dirtyKeys) {
                Long tenantId = Long.valueOf(key.get("tenantId").toString());
                Long storeId = Long.valueOf(key.get("storeId").toString());
                LocalDate bizDate = toLocalDate(key.get("bizDate"));
                mapper.deleteStoreDishDaily(tenantId, storeId, bizDate);
                mapper.deleteStoreDaily(tenantId, storeId, bizDate);
                mapper.insertStoreDaily(tenantId, storeId, bizDate);
                mapper.insertStoreDishDaily(tenantId, storeId, bizDate);
                mapper.deleteDirtyKey(tenantId, storeId, bizDate);
            }
            return dirtyKeys.size();
        });
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        return LocalDate.parse(value.toString());
    }
}
