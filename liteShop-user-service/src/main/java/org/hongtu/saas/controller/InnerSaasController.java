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

/**
 * SaaS 权益核心内部接口：供其它服务通过 Feign 做配额校验、消费与释放。
 */
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
        if (dto.getConsumeKey() == null || dto.getConsumeKey().isBlank()
                || dto.getIdempotencyKey() == null || dto.getIdempotencyKey().isBlank()) {
            throw new org.hongtu.exception.BusinessException(400, "释放配额必须引用原消费事件");
        }
        service.refundConsumption(TenantContext.getTenantId(), dto.getConsumeKey(), dto.getIdempotencyKey());
        return Result.success(new EntitlementDecisionDTO().setAllowed(true));
    }

    private EntitlementDecisionDTO copy(EntitlementDecision source) {
        EntitlementDecisionDTO target = new EntitlementDecisionDTO();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
