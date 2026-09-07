package org.hongtu.restaurant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.StoreSummaryDTO;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.service.StoreInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;

/** 仅供服务间调用的餐饮数据归属校验接口。 */
@RestController
@RequestMapping("/inner/restaurant")
public class InnerRestaurantController {

    @Resource
    private StoreInfoService storeInfoService;

    @GetMapping("/stores/validate")
    public Result<Boolean> validateStoreIds(@RequestParam List<Long> storeIds) {
        List<Long> normalized = storeIds == null ? List.of()
                : new LinkedHashSet<>(storeIds).stream().toList();
        if (normalized.isEmpty()) {
            return Result.success(true);
        }
        long count = storeInfoService.count(new LambdaQueryWrapper<StoreInfo>()
                .in(StoreInfo::getId, normalized));
        return Result.success(count == normalized.size());
    }

    @GetMapping("/stores/options")
    public Result<List<StoreSummaryDTO>> getStoreOptions(@RequestParam List<Long> storeIds) {
        List<Long> normalized = storeIds == null ? List.of()
                : new LinkedHashSet<>(storeIds).stream().toList();
        if (normalized.isEmpty()) {
            return Result.success(List.of());
        }
        List<StoreSummaryDTO> stores = storeInfoService.list(
                        new LambdaQueryWrapper<StoreInfo>().in(StoreInfo::getId, normalized))
                .stream()
                .map(store -> new StoreSummaryDTO(store.getId(), store.getStoreName()))
                .toList();
        return Result.success(stores);
    }
}
