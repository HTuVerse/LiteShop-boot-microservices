package org.hongtu.service;

import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.StoreDirectoryMapper;
import org.hongtu.DTO.StoreDirectory;
import org.hongtu.api.dto.StoreDirectoryDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 接收餐厅服务的单向门店投影，提供用户服务内的授权校验。 */
@Service
@RequiredArgsConstructor
public class StoreDirectoryService {
    private final StoreDirectoryMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public void sync(List<StoreDirectoryDTO> stores) {
        if (stores == null || stores.isEmpty() || stores.size() > 100)
            throw new BusinessException(400, "门店目录同步批次不合法");
        Long tenantId = TenantContext.getTenantId();
        for (StoreDirectoryDTO store : stores) {
            if (store == null || store.getStoreId() == null || store.getStoreId() <= 0
                    || store.getStoreName() == null || store.getStoreName().isBlank()
                    || store.getStoreName().length() > 100 || store.getSyncVersion() < 0)
                throw new BusinessException(400, "门店目录数据不合法");
            mapper.upsert(tenantId, store.getStoreId(), store.getStoreName(),
                    store.isActive(), store.getSyncVersion());
        }
    }

    public boolean allActive(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return true;
        return mapper.activeByIds(TenantContext.getTenantId(), ids).size() == ids.size();
    }

    public Map<Long, String> names(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return mapper.activeByIds(TenantContext.getTenantId(), ids).stream()
                .collect(Collectors.toMap(StoreDirectory::getStoreId, StoreDirectory::getStoreName));
    }
}
