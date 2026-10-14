package org.hongtu.api.client;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="liteShop-config-service",contextId="configDefaultsClient")
public interface ConfigFeignClient {
    /** 读取平台运行模型配置，仅用于服务间调用。 */
    @GetMapping("/inner/config/ai-model") Result<org.hongtu.api.dto.AiModelRuntimeConfigDTO> aiModel();
    /** 读取指定租户已启用的支付账户，未配置时 data 为空。 */
    @GetMapping("/inner/config/payment-account")
    Result<TenantPaymentRuntimeDTO> paymentAccount(@RequestParam("tenantId") Long tenantId,
                                                   @RequestParam("channel") String channel);
    @GetMapping("/inner/config/website") Result<org.hongtu.api.dto.WebsiteConfigDTO> website();
    @PostMapping("/inner/config/initialize") Result<Boolean> initialize(@RequestParam("appName") String appName);
}
