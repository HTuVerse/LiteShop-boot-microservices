package org.hongtu.restaurant.config;

import jakarta.annotation.Resource;
import org.hongtu.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.net.URI;

/** 飞鹅云参数代理；每次按当前租户从配置服务读取。 */
@Component
public class FeieyunPrinterConfig {
    @Resource private TenantRestaurantConfig tenantConfig;

    public String getApiUrl() {
        String url = tenantConfig.get("printer_api_url");
        try {
            URI uri = URI.create(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && "api.feieyun.cn".equalsIgnoreCase(uri.getHost())
                    && "/Api/Open/".equals(uri.getPath()) && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null) return url;
        } catch (IllegalArgumentException ignored) {
            // 错误地址统一交给下面的业务异常处理。
        }
        throw new BusinessException("飞鹅云接口地址必须为 https://api.feieyun.cn/Api/Open/");
    }
    public String getUser() { return required("printer_user", "飞鹅云账号"); }
    public String getUkey() { return required("printer_ukey", "飞鹅云 UKEY"); }
    public String getSn() { return tenantConfig.get("printer_sn"); }
    public String getPublicKey(Long tenantId) { return tenantConfig.get(tenantId, "printer_public_key"); }
    public String getScanPrivateKey(Long tenantId) { return tenantConfig.get(tenantId, "printer_scan_private_key"); }

    private String required(String key, String name) {
        String value = tenantConfig.get(key);
        if (value == null || value.isBlank()) throw new BusinessException(name + "未配置");
        return value;
    }
}
