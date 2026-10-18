package org.hongtu.api.client;

import org.hongtu.Result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 文件服务查询配置域中是否仍有业务引用。 */
@FeignClient(name = "liteShop-config-service", contextId = "configResourceReferenceClient")
public interface ConfigResourceReferenceClient {
    @GetMapping("/inner/resource-reference") Result<Boolean> used(@RequestParam("url") String url);
}
