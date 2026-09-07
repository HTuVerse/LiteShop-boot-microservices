package org.hongtu.api.client;
import org.hongtu.Result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="liteShop-config-service",contextId="configDefaultsClient")
public interface ConfigFeignClient {
    @GetMapping("/inner/config/website") Result<org.hongtu.api.dto.WebsiteConfigDTO> website();
    @PostMapping("/inner/config/initialize") Result<Boolean> initialize(@RequestParam("appName") String appName);
}
