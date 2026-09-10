package org.hongtu.saas.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.saas.service.SaasEntitlementService;
import org.hongtu.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/saas/entitlements")
@SaCheckLogin
public class SaasEntitlementController {
    @Resource private SaasEntitlementService service;

    @GetMapping("/me") public Result<?> current() { return Result.success(service.resolveCurrent()); }
    @GetMapping("/check") public Result<?> check(@RequestParam String featureCode,
                                                 @RequestParam(defaultValue = "0") BigDecimal amount,
                                                 @RequestParam(required = false) BigDecimal currentUsage) {
        return Result.success(service.checkQuota(TenantContext.getTenantId(), featureCode, currentUsage, amount));
    }
    @PostMapping("/consume") public Result<?> consume(@RequestBody EntitlementMutationDTO dto) {
        return Result.success(service.consume(TenantContext.getTenantId(), dto.getFeatureCode(), dto.getAmount(), dto.getIdempotencyKey()));
    }
    @PostMapping("/release") public Result<?> release(@RequestBody EntitlementMutationDTO dto) {
        return Result.success(service.release(TenantContext.getTenantId(), dto.getFeatureCode(), dto.getAmount(), dto.getIdempotencyKey()));
    }
}
