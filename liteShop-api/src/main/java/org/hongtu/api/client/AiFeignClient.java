package org.hongtu.api.client;

import org.hongtu.Result.Result;
import org.hongtu.api.dto.AiModelConnectionTestDTO;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** AI 服务内部诊断接口。 */
@FeignClient(name = "liteShop-ai-service", contextId = "aiModelTestClient")
public interface AiFeignClient {
    /** 使用指定模型配置执行独立的连通性测试。 */
    @PostMapping("/inner/ai/model/test")
    Result<AiModelConnectionTestDTO> testModel(@RequestBody AiModelRuntimeConfigDTO config);
}
