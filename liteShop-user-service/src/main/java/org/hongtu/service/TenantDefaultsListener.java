package org.hongtu.service;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
@Component @Slf4j
public class TenantDefaultsListener {
    @Resource private ConfigFeignClient config;
    @TransactionalEventListener
    public void initialize(TenantCreatedEvent event) {
        try { TenantScope.run(event.tenantId(), () -> FeignResultUtil.unwrap(config.initialize(event.tenantName()))); }
        catch (Exception ex) { log.warn("租户 {} 的配置服务暂不可用，首次读取配置时将补齐默认项",event.tenantId(),ex); }
    }
}
