package org.hongtu.config.controller;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.client.AiFeignClient;
import org.hongtu.api.dto.AiModelConnectionTestDTO;
import org.hongtu.api.dto.AiModelConfigDTO;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.config.service.AiModelConfigService;
import org.hongtu.exception.BusinessException;
import org.springframework.web.bind.annotation.*;
/** 平台 AI 模型配置接口，仅超级管理员能读取和修改表单。 */
@RestController
@RequiredArgsConstructor
public class AiModelConfigController {
    private final AiModelConfigService service;
    private final AiFeignClient aiClient;
    /** 读取配置，已有 Key 不回显。 */
    @SaCheckRole("SUPER_ADMIN") @GetMapping("/config/ai-model/admin")
    public Result<AiModelConfigDTO> get() { return Result.success(service.getForEdit()); }
    /** 保存平台配置，后续 AI 请求直接使用新版本。 */
    @SaCheckRole("SUPER_ADMIN") @PutMapping("/config/ai-model/admin")
    public Result<AiModelConfigDTO> save(@Valid @RequestBody AiModelConfigDTO config) { return Result.success(service.save(config, StpUtil.getLoginIdAsLong())); }
    /** 测试当前表单中的模型接口配置，不保存配置且不进入 AI 店长业务。 */
    @SaCheckRole("SUPER_ADMIN") @PostMapping("/config/ai-model/admin/test")
    public Result<AiModelConnectionTestDTO> test(@Valid @RequestBody AiModelConfigDTO config) {
        try {
            return Result.success(FeignResultUtil.unwrap(aiClient.testModel(service.resolveForTest(config))));
        } catch (BusinessException error) {
            throw error;
        } catch (FeignException error) {
            throw new BusinessException(502,
                    "模型接口测试服务不可用，请确认 AI 服务已更新并正常注册", error);
        }
    }
    /** 内部接口由公共拦截器校验服务凭据，网关禁止外部访问。 */
    @GetMapping("/inner/config/ai-model")
    public Result<AiModelRuntimeConfigDTO> runtime() { return Result.success(service.getRuntime()); }
}
