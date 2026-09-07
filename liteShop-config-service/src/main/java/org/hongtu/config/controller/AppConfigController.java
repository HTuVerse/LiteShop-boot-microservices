package org.hongtu.config.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.config.DTO.AppConfig;
import org.hongtu.config.VO.AppConfigVO;
import org.hongtu.config.enums.ConfigGroupEnum;
import org.hongtu.config.service.AppConfigService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统配置 Controller
 *
 * @author Ht
 */
@RestController
@RequestMapping("/config/app")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService appConfigService;
    private final org.hongtu.config.service.TenantConfigDefaults defaults;

    // ==================== 小程序端接口（公开） ====================

    /**
     * 获取小程序端所有配置（用于前端初始化）
     * 包含：应用信息、默认图片、轮播图等
     */
    @GetMapping("/miniapp")
    @SaIgnore
    public Result<AppConfigVO> getMiniappConfig() {
        AppConfigVO config = appConfigService.getMiniappConfig();
        return Result.success(config);
    }

    /**
     * 获取默认图片配置
     */
    @GetMapping("/default-images")
    @SaIgnore
    public Result<Map<String, String>> getDefaultImageConfig() {
        Map<String, String> imageConfig = appConfigService.getDefaultImageConfig();
        return Result.success(imageConfig);
    }

    /**
     * 获取单个配置值
     */
    @GetMapping("/value/{key}")
    @SaIgnore
    public Result<String> getConfigValue(@PathVariable String key) {
        String value = appConfigService.getConfigValue(key);
        return Result.success("查询成功", value);
    }

    /**
     * 批量获取配置值
     */
    @PostMapping("/values")
    @SaIgnore
    public Result<Map<String, String>> getConfigValues(@RequestBody List<String> keys) {
        Map<String, String> values = appConfigService.getConfigsByKeys(keys);
        return Result.success(values);
    }

    // ==================== 管理端接口（需要认证） ====================

    /**
     * 获取所有配置列表（管理端）
     */
    @GetMapping("/admin/list")
    @SaCheckPermission(value = {"config:basic"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<AppConfig>> getAllConfigs(
            @RequestParam(required = false) Integer configGroup) {

        defaults.ensureDefaults(null);
        List<AppConfig> configs;
        if (configGroup != null) {
            ConfigGroupEnum groupEnum = ConfigGroupEnum.fromCode(configGroup);
            configs = appConfigService.getByConfigGroup(groupEnum);
        } else {
            configs = appConfigService.list();
        }
        return Result.success(configs);
    }

    /**
     * 获取配置详情
     */
    @GetMapping("/admin/{id}")
    @SaCheckPermission(value = {"config:basic"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<AppConfig> getConfigById(@PathVariable Long id) {
        AppConfig config = appConfigService.getById(id);
        if (config == null) {
            return Result.error("配置不存在");
        }
        return Result.success(config);
    }

    /**
     * 根据配置键获取配置
     */
    @GetMapping("/admin/key/{configKey}")
    @SaCheckPermission(value = {"config:basic"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<AppConfig> getConfigByKey(@PathVariable String configKey) {
        AppConfig config = appConfigService.getByConfigKey(configKey);
        if (config == null) {
            return Result.error("配置不存在");
        }
        return Result.success(config);
    }

    /**
     * 新增配置
     */
    @PostMapping("/admin/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:basic:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addConfig(@RequestBody AppConfig config) {
        // 检查配置键是否已存在
        AppConfig existing = appConfigService.getByConfigKey(config.getConfigKey());
        if (existing != null) {
            return Result.error("配置键已存在");
        }

        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        if (config.getEditable() == null) {
            config.setEditable(1);
        }
        boolean success = appConfigService.save(config);
        if (success) {
            appConfigService.refreshCache();
        }
        return success ? Result.success(true) : Result.error("新增失败");
    }

    /**
     * 更新配置
     */
    @PutMapping("/admin/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:basic:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateConfig(@RequestBody AppConfig config) {
        if (config.getId() == null) {
            return Result.error("配置ID不能为空");
        }

        // 检查是否允许修改
        AppConfig existing = appConfigService.getById(config.getId());
        if (existing != null && existing.getEditable() != null && existing.getEditable() == 0) {
            return Result.error("该配置不允许修改");
        }

        config.setUpdatedAt(LocalDateTime.now());
        boolean success = appConfigService.updateById(config);
        if (success) {
            appConfigService.refreshCache();
        }
        return success ? Result.success(true) : Result.error("更新失败");
    }

    /**
     * 更新配置值（简化接口）
     */
    @PutMapping("/admin/update-value")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:basic:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateConfigValue(
            @RequestParam String configKey,
            @RequestParam String configValue) {

        boolean success = appConfigService.updateConfigValue(configKey, configValue);
        return success ? Result.success(true) : Result.error("更新失败");
    }

    /**
     * 批量更新配置值
     */
    @PutMapping("/admin/batch-update")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:basic:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> batchUpdateConfigs(@RequestBody Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            if (!appConfigService.updateConfigValue(entry.getKey(), entry.getValue()))
                throw new org.hongtu.exception.BusinessException(400,"配置保存失败：" + entry.getKey());
        }
        return Result.success(true);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/admin/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:basic:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteConfig(@PathVariable Long id) {
        // 检查是否允许删除
        AppConfig existing = appConfigService.getById(id);
        if (existing != null && existing.getEditable() != null && existing.getEditable() == 0) {
            return Result.error("该配置不允许删除");
        }

        boolean success = appConfigService.removeById(id);
        if (success) {
            appConfigService.refreshCache();
        }
        return success ? Result.success(true) : Result.error("删除失败");
    }

    /**
     * 刷新配置缓存
     */
    @PostMapping("/admin/refresh-cache")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:basic:refresh"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> refreshCache() {
        appConfigService.refreshCache();
        return Result.success(true);
    }

    /**
     * 获取配置分组列表
     */
    @GetMapping("/admin/groups")
    @SaCheckPermission(value = {"config:basic"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<ConfigGroupEnum[]> getConfigGroups() {
        return Result.success(ConfigGroupEnum.values());
    }
}
