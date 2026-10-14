package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.VO.TenantAnalysisQuery;
import org.hongtu.restaurant.VO.analysis.TenantAnalysisVO;
import org.hongtu.restaurant.service.TenantAnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户级经营分析接口。
 */
@RestController
@RequestMapping({"/restaurant-analysis/tenant", "/tenant-analysis"})
public class TenantAnalysisController {

    private final TenantAnalysisService tenantAnalysisService;

    public TenantAnalysisController(TenantAnalysisService tenantAnalysisService) {
        this.tenantAnalysisService = tenantAnalysisService;
    }

    /** 查询租户核心经营总览。 */
    @PostMapping("/overview")
    @SaCheckPermission(value = {"restaurant:tenant-analysis", "restaurant:analysis"},
            mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<TenantAnalysisVO.Overview> overview(@Valid @RequestBody TenantAnalysisQuery query) {
        return Result.success(tenantAnalysisService.overview(query));
    }

    /** 查询门店销售和增长排行榜。 */
    @PostMapping("/store-ranking")
    @SaCheckPermission(value = {"restaurant:tenant-analysis", "restaurant:analysis"},
            mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<TenantAnalysisVO.StoreRanking> storeRanking(@Valid @RequestBody TenantAnalysisQuery query) {
        return Result.success(tenantAnalysisService.storeRanking(query));
    }

    /** 查询租户菜品销售、毛利和滞销排行。 */
    @PostMapping("/dish-ranking")
    @SaCheckPermission(value = {"restaurant:tenant-analysis", "restaurant:analysis"},
            mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<TenantAnalysisVO.DishRanking> dishRanking(@Valid @RequestBody TenantAnalysisQuery query) {
        return Result.success(tenantAnalysisService.dishRanking(query));
    }

    /** 查询门店与菜品横向经营表现矩阵。 */
    @PostMapping("/store-dish-matrix")
    @SaCheckPermission(value = {"restaurant:tenant-analysis", "restaurant:analysis"},
            mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<TenantAnalysisVO.StoreDishMatrix> storeDishMatrix(@Valid @RequestBody TenantAnalysisQuery query) {
        return Result.success(tenantAnalysisService.storeDishMatrix(query));
    }

    /** 查询选定菜品在各门店的销量、销售额、销售占比和排名。 */
    @PostMapping("/store-dish-comparison")
    @SaCheckPermission(value = {"restaurant:tenant-analysis", "restaurant:analysis"},
            mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<TenantAnalysisVO.StoreDishComparison> storeDishComparison(
            @Valid @RequestBody TenantAnalysisQuery query) {
        return Result.success(tenantAnalysisService.storeDishComparison(query));
    }
}
