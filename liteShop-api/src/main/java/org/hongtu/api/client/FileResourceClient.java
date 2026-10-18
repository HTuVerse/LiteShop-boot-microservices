package org.hongtu.api.client;

import org.hongtu.Result.Result;
import org.hongtu.api.dto.ResourceBinding;
import org.hongtu.api.dto.ResourceBindingRequest;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

/** 业务服务按资源 ID 获取公开访问地址，隐藏底层存储驱动。 */
@FeignClient(name = "liteShop-file-service", contextId = "fileResourceClient")
public interface FileResourceClient {
    @GetMapping("/inner/file-upload/resources/{id}/public-url")
    Result<String> publicUrl(@PathVariable("id") Long id);

    /** 先登记新引用，保证业务提交前资源不会被并发删除。 */
    @PostMapping("/inner/file-upload/references/prepare")
    Result<ResourceBindingPlan> prepare(@RequestBody ResourceBindingRequest request);

    /** 业务提交后清除不再使用的旧引用。 */
    @PostMapping("/inner/file-upload/references/complete")
    Result<Void> complete(@RequestBody ResourceBindingPlan plan);

    /** 根据业务 ID 读取全部已登记的位置。 */
    @GetMapping("/inner/file-upload/references")
    Result<List<ResourceBinding>> references(@RequestParam("tenantId") Long tenantId,
                                             @RequestParam("businessType") String businessType,
                                             @RequestParam("businessId") Long businessId);

    /** 历史回填只做完整 URL 的唯一匹配，不猜测外部资源归属。 */
    @GetMapping("/inner/file-upload/references/match")
    Result<Long> match(@RequestParam("tenantId") Long tenantId, @RequestParam("url") String url);
}
