package org.hongtu.controller;

import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.ResourceBinding;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.hongtu.api.dto.ResourceBindingRequest;
import org.hongtu.storage.ResourceReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 仅供服务间调用的资源引用登记与读取接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/file-upload/references")
public class ResourceBindingController {
    private final ResourceReferenceService references;

    /** 在业务提交前登记新引用。 */
    @PostMapping("/prepare")
    public Result<ResourceBindingPlan> prepare(@RequestBody ResourceBindingRequest request) {
        return Result.success(references.prepare(request));
    }

    /** 提交后只删除准备阶段存在的旧引用。 */
    @PostMapping("/complete")
    public Result<Void> complete(@RequestBody ResourceBindingPlan plan) {
        references.complete(plan);
        return Result.success();
    }

    /** 查询业务所用资源的位置与当前公开 URL。 */
    @GetMapping
    public Result<List<ResourceBinding>> references(@RequestParam Long tenantId,
                                                    @RequestParam String businessType,
                                                    @RequestParam Long businessId) {
        return Result.success(references.references(tenantId, businessType, businessId));
    }

    /** 只返回可由当前租户公开使用且唯一匹配的历史资源 ID。 */
    @GetMapping("/match")
    public Result<Long> match(@RequestParam Long tenantId, @RequestParam String url) {
        return Result.success(references.matchForTenant(tenantId, url));
    }
}
