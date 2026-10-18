package org.hongtu.api.client;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="liteShop-config-service",contextId="configDefaultsClient")
public interface ConfigFeignClient {
    /** 内部服务读取平台邮件与短信运行配置。 */
    @GetMapping("/inner/config/platform-message")
    Result<org.hongtu.api.dto.PlatformServiceRuntimeDTO> platformMessage();
    /** 内部服务读取平台文件存储运行配置。 */
    @GetMapping("/inner/config/platform-file")
    Result<org.hongtu.api.dto.PlatformServiceRuntimeDTO> platformFile();
    /** 历史资源读取其上传时的存储配置。 */
    @GetMapping("/inner/config/platform-file-profile")
    Result<org.hongtu.api.dto.PlatformServiceRuntimeDTO> platformFileProfile(@RequestParam("profileId") Long profileId);
    /** 本地文件读取只需要当前目录，不传递云存储密钥。 */
    @GetMapping("/inner/config/platform-file-path")
    Result<String> platformFilePath();
    /** 读取平台运行模型配置，仅用于服务间调用。 */
    @GetMapping("/inner/config/ai-model") Result<org.hongtu.api.dto.AiModelRuntimeConfigDTO> aiModel();
    /** 读取指定租户已启用的支付账户，未配置时 data 为空。 */
    @GetMapping("/inner/config/payment-account")
    Result<TenantPaymentRuntimeDTO> paymentAccount(@RequestParam("tenantId") Long tenantId,
                                                   @RequestParam("channel") String channel);
    @GetMapping("/inner/config/website") Result<org.hongtu.api.dto.WebsiteConfigDTO> website();
    @PostMapping("/inner/config/initialize") Result<Boolean> initialize(@RequestParam("appName") String appName);
}
