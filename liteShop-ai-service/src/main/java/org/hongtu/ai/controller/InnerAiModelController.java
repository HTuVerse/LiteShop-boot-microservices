package org.hongtu.ai.controller;

import org.hongtu.Result.Result;
import org.hongtu.ai.client.AiAvailabilityService;
import org.hongtu.api.dto.AiModelConnectionTestDTO;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 模型内部诊断接口，仅供配置服务调用。 */
@RestController
@RequestMapping("/inner/ai/model")
public class InnerAiModelController {
    private final AiAvailabilityService availability;

    public InnerAiModelController(AiAvailabilityService availability) {
        this.availability = availability;
    }

    /** 独立测试指定模型接口，不进入 AI 店长业务链路。 */
    @PostMapping("/test")
    public Result<AiModelConnectionTestDTO> test(@RequestBody AiModelRuntimeConfigDTO config) {
        return Result.success(availability.testConnection(config));
    }
}
