package org.hongtu.config.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.WebsiteConfigDTO;
import org.hongtu.config.service.WebsiteConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WebsiteConfigController {
    private final WebsiteConfigService service;

    @SaIgnore
    @GetMapping("/config/website/public")
    public Result<Map<String, Object>> publicConfig() {
        return Result.success(publicFields(service.getPublished()));
    }

    /** Explicit public allowlist; security thresholds and audit information stay server-side. */
    public static Map<String, Object> publicFields(WebsiteConfigDTO c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revision", c.getRevision());
        m.put("systemName", c.getSystemName());
        m.put("description", c.getDescription());
        m.put("watermarkEnabled", c.getWatermarkEnabled());
        m.put("watermarkMode", c.getWatermarkMode());
        m.put("watermarkText", c.getWatermarkText());
        m.put("loginTitle", c.getLoginTitle());
        m.put("loginDescription", c.getLoginDescription());
        m.put("defaultLanguage", c.getDefaultLanguage());
        m.put("captchaEnabled", c.getCaptchaEnabled());
        m.put("captchaType", c.getCaptchaType());
        m.put("maintenanceEnabled", c.getMaintenanceEnabled());
        m.put("maintenanceMessage", c.getMaintenanceMessage());
        m.put("seoTitle", c.getSeoTitle());
        m.put("seoKeywords", c.getSeoKeywords());
        m.put("seoDescription", c.getSeoDescription());
        m.put("contactEmail", c.getContactEmail());
        m.put("contactPhone", c.getContactPhone());
        m.put("contactAddress", c.getContactAddress());
        m.put("copyright", c.getCopyright());
        m.put("icpNumber", c.getIcpNumber());
        m.put("publicSecurityNumber", c.getPublicSecurityNumber());
        return m;
    }

    @SaCheckRole("SUPER_ADMIN")
    @GetMapping("/config/website/admin")
    public Result<WebsiteConfigDTO> get() { return Result.success(service.getForEdit()); }

    @SaCheckRole("SUPER_ADMIN")
    @PutMapping("/config/website/admin")
    public Result<WebsiteConfigDTO> publish(@Valid @RequestBody WebsiteConfigDTO config) {
        return Result.success(service.publish(config, StpUtil.getLoginIdAsLong()));
    }

    // The gateway blocks /inner/**; TenantInterceptor authenticates the internal-call secret.
    @GetMapping("/inner/config/website")
    public Result<WebsiteConfigDTO> internalConfig() { return Result.success(service.getPublished()); }
}
