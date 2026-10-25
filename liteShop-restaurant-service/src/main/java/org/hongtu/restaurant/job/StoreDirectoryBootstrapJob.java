package org.hongtu.restaurant.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.StoreDirectoryDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DTO.StoreDirectoryRow;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.hongtu.utils.RedisJobLease;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 周期性补齐用户服务门店目录，增量变更仍由本地发件箱优先投递。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreDirectoryBootstrapJob {
    private final StoreInfoMapper stores;
    private final UserFeignClient users;
    private final RedisJobLease lease;

    @Scheduled(initialDelayString = "${liteshop.store-directory.initial-delay-ms:5000}",
            fixedDelayString = "${liteshop.store-directory.sync-interval-ms:300000}")
    public void sync() {
        lease.runIfAcquired("restaurant-store-directory-bootstrap", Duration.ofMinutes(15), () -> {
            long cursor = 0;
            while (true) {
                long after = cursor;
                List<StoreDirectoryRow> rows = TenantIgnore.get(() -> stores.listDirectoryAfterId(after));
                if (rows.isEmpty()) return;
                cursor = rows.getLast().getId();
                Map<Long, List<StoreDirectoryRow>> groups = rows.stream()
                        .collect(Collectors.groupingBy(StoreDirectoryRow::getTenantId));
                groups.forEach((tenantId, tenantStores) -> {
                    try {
                        TenantScope.run(tenantId, () -> {
                            List<StoreDirectoryDTO> batch = tenantStores.stream().map(row -> {
                                StoreDirectoryDTO item = new StoreDirectoryDTO();
                                item.setStoreId(row.getId());
                                item.setStoreName(row.getStoreName());
                                item.setActive(true);
                                item.setSyncVersion(0);
                                return item;
                            }).toList();
                            FeignResultUtil.check(users.syncStoreDirectory(batch));
                        });
                    } catch (RuntimeException error) {
                        log.error("门店目录初始化失败，下轮重试: tenantId={}", tenantId, error);
                    }
                });
                if (rows.size() < 100) return;
            }
        });
    }
}
