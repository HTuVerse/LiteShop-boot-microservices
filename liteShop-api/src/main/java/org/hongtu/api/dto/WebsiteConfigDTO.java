package org.hongtu.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** 管理端网站的全局配置，与商户小程序配置分开。 */
@Data
public class WebsiteConfigDTO {
    @NotNull @Min(0) private Long revision = 0L;
    @NotBlank @Size(max = 80) private String systemName = "LiteShop";
    @NotNull @Size(max = 255) private String description = "商户运营管理平台";
    @NotNull private Boolean watermarkEnabled = false;
    @NotNull @Pattern(regexp = "username|username_time|system|custom") private String watermarkMode = "username";
    @NotNull @Size(max = 100) private String watermarkText = "";
    @NotNull @Size(max = 100) private String loginTitle = "欢迎回来";
    @NotNull @Size(max = 255) private String loginDescription = "使用已开通管理权限的账号登录。";
    @NotNull @Pattern(regexp = "zh|en") private String defaultLanguage = "zh";
    @NotNull private Boolean captchaEnabled = false;
    @NotNull @Pattern(regexp = "image") private String captchaType = "image";
    @Min(1) @Max(100) private Integer maxLoginAttempts;
    @NotNull @Min(1) @Max(1440) private Integer lockMinutes = 10;
    @NotNull private Boolean maintenanceEnabled = false;
    @NotNull @Size(max = 255) private String maintenanceMessage = "系统正在维护，请稍后再试。";
    @NotNull @Size(max = 100) private String seoTitle = "";
    @NotNull @Size(max = 255) private String seoKeywords = "";
    @NotNull @Size(max = 500) private String seoDescription = "";
    @NotNull @Email @Size(max = 120) private String contactEmail = "";
    @NotNull @Size(max = 50) private String contactPhone = "";
    @NotNull @Size(max = 255) private String contactAddress = "";
    @NotNull @Size(max = 255) private String copyright = "";
    @NotNull @Size(max = 100) private String icpNumber = "";
    @NotNull @Size(max = 100) private String publicSecurityNumber = "";

    @AssertTrue(message = "自定义水印启用时必须填写水印内容")
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isWatermarkValid() {
        return !Boolean.TRUE.equals(watermarkEnabled) || !"custom".equals(watermarkMode)
                || (watermarkText != null && !watermarkText.isBlank());
    }
    @AssertTrue(message = "维护模式开启时必须填写维护提示")
    @com.alibaba.fastjson.annotation.JSONField(serialize = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isMaintenanceValid() {
        return !Boolean.TRUE.equals(maintenanceEnabled)
                || (maintenanceMessage != null && !maintenanceMessage.isBlank());
    }
}
