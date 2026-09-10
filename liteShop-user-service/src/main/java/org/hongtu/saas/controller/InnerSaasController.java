package org.hongtu.saas.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.saas.service.SaasEntitlementService;
import org.hongtu.saas.view.EntitlementDecision;
import org.hongtu.tenant.TenantContext;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inner/saas")
@SaIgnore
public class InnerSaasController {
    @Resource private SaasEntitlementService service;

    @PostMapping("/check") public Result<EntitlementDecisionDTO> check(@RequestBody EntitlementMutationDTO dto) {
        return Result.success(copy(service.checkQuota(TenantContext.getTenantId(), dto.getFeatureCode(), dto.getCurrentUsage(), dto.getAmount())));
    }
    @PostMapping("/consume") public Result<EntitlementDecisionDTO> consume(@RequestBody EntitlementMutationDTO dto) {
        return Result.success(copy(service.consume(TenantContext.getTenantId(), dto.getFeatureCode(), dto.getAmount(), dto.getIdempotencyKey())));
    }
    @PostMapping("/release") public Result<EntitlementDecisionDTO> release(@RequestBody EntitlementMutationDTO dto) {
        return Result.success(copy(service.release(TenantContext.getTenantId(), dto.getFeatureCode(), dto.getAmount(), dto.getIdempotencyKey())));
    }
    private EntitlementDecisionDTO copy(EntitlementDecision source) {
        EntitlementDecisionDTO target = new EntitlementDecisionDTO();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
