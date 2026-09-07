package org.hongtu.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.config.DAO.AppConfigMapper;
import org.hongtu.config.DTO.AppConfig;
import org.hongtu.config.DTO.Banner;
import org.hongtu.config.VO.AppConfigVO;
import org.hongtu.config.VO.BannerVO;
import org.hongtu.config.enums.ConfigGroupEnum;
import org.hongtu.config.service.AppConfigService;
import org.hongtu.config.service.BannerService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统配置 Service 实现类
 *
 * @author Ht
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppConfigServiceImpl extends ServiceImpl<AppConfigMapper, AppConfig> implements AppConfigService {
    @jakarta.annotation.Resource private org.hongtu.config.service.TenantConfigDefaults defaults;

    private final BannerService bannerService;

    /**
     * 配置缓存
     */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, String>> tenantCaches = new ConcurrentHashMap<>();

    /**
     * 配置键常量
     */
    public static final String KEY_APP_NAME = "app_name";
    public static final String KEY_APP_LOGO = "app_logo";
    public static final String KEY_APP_DESC = "app_desc";
    public static final String KEY_HOME_BACKGROUND_IMAGE = "home_background_image";
    public static final String KEY_ORDER_BACKGROUND_IMAGE ="order_background_image";
    public static final String KEY_DEFAULT_DISH_IMAGE = "default_dish_image";
    public static final String KEY_DEFAULT_AVATAR = "default_avatar";
    public static final String KEY_DEFAULT_EMPTY_IMAGE = "default_empty_image";
    public static final String KEY_DEFAULT_ORDER_IMAGE = "default_order_image";
    public static final String KEY_THEME_COLOR = "theme_color";
    public static final String KEY_SERVICE_PHONE = "service_phone";

    /**
     * 配置键常量 - 部署配置
     */
    public static final String KEY_H5_BASE_URL = "h5_base_url";
    public static final String KEY_MINIAPP_BASE_URL = "miniapp_base_url";
    public static final String KEY_MINIAPP_APPID = "miniapp_appid";
    public static final String KEY_QRCODE_DEFAULT_PLATFORM = "qrcode_default_platform";

    private ConcurrentHashMap<String, String> currentCache() {
        return tenantCaches.computeIfAbsent(TenantContext.getTenantId(), id -> new ConcurrentHashMap<>());
    }

    @Override
    public String getConfigValue(String configKey) {
        ConcurrentHashMap<String, String> configCache = currentCache();
        // 先从缓存获取
        String cachedValue = configCache.get(configKey);
        if (cachedValue != null) {
            return cachedValue;
        }
        
        // 缓存中没有，从数据库获取
        AppConfig config = getByConfigKey(configKey);
        if (config != null && config.getConfigValue() != null) {
            configCache.put(configKey, config.getConfigValue());
            return config.getConfigValue();
        }
        return null;
    }

    @Override
    public String getConfigValue(String configKey, String defaultValue) {
        String value = getConfigValue(configKey);
        return value != null ? value : defaultValue;
    }

    @Override
    public AppConfig getByConfigKey(String configKey) {
        LambdaQueryWrapper<AppConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppConfig::getConfigKey, configKey);
        AppConfig config = getOne(wrapper);
        if (config == null) { defaults.ensureKey(configKey); config = getOne(wrapper); }
        return config;
    }

    @Override
    public List<AppConfig> getByConfigGroup(ConfigGroupEnum configGroup) {
        LambdaQueryWrapper<AppConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppConfig::getConfigGroup, configGroup)
                .orderByAsc(AppConfig::getId);
        return list(wrapper);
    }

    @Override
    public Map<String, String> getConfigsByKeys(List<String> configKeys) {
        Map<String, String> result = new HashMap<>();
        for (String key : configKeys) {
            String value = getConfigValue(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public boolean updateConfigValue(String configKey, String configValue) {
        defaults.ensureKey(configKey);
        ConcurrentHashMap<String, String> configCache = currentCache();
        LambdaUpdateWrapper<AppConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AppConfig::getConfigKey, configKey)
                .set(AppConfig::getConfigValue, configValue)
                .set(AppConfig::getUpdatedAt, LocalDateTime.now());
        boolean success = update(wrapper);
        
        if (success) {
            // 更新缓存
            Runnable apply = () -> { if (configValue == null) configCache.remove(configKey); else configCache.put(configKey, configValue); };
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { apply.run(); }
                });
            } else apply.run();
        }
        return success;
    }

    @Override
    public AppConfigVO getMiniappConfig() {
        AppConfigVO vo = new AppConfigVO();
        
        // 基础配置
        vo.setAppName(getConfigValue(KEY_APP_NAME, "LiteShop"));
        vo.setAppLogo(getConfigValue(KEY_APP_LOGO, ""));
        vo.setAppDesc(getConfigValue(KEY_APP_DESC, ""));
        vo.setHomeBackgroundImage(getConfigValue(KEY_HOME_BACKGROUND_IMAGE, ""));
        vo.setThemeColor(getConfigValue(KEY_THEME_COLOR, "#FF6B35"));
        vo.setServicePhone(getConfigValue(KEY_SERVICE_PHONE, ""));
        vo.setOrderBackgroundImage(getConfigValue(KEY_ORDER_BACKGROUND_IMAGE, ""));

        // 默认图片配置
        vo.setDefaultDishImage(getConfigValue(KEY_DEFAULT_DISH_IMAGE, ""));
        vo.setDefaultAvatar(getConfigValue(KEY_DEFAULT_AVATAR, ""));
        vo.setDefaultEmptyImage(getConfigValue(KEY_DEFAULT_EMPTY_IMAGE, ""));
        vo.setDefaultOrderImage(getConfigValue(KEY_DEFAULT_ORDER_IMAGE, ""));
        // 轮播图配置
        vo.setHomeBanners(convertToBannerVOList(bannerService.getHomeBanners()));
        vo.setOrderBanners(convertToBannerVOList(bannerService.getOrderBanners()));
        
        return vo;
    }

    @Override
    public Map<String, String> getDefaultImageConfig() {
        Map<String, String> imageConfig = new HashMap<>();
        imageConfig.put("defaultDishImage", getConfigValue(KEY_DEFAULT_DISH_IMAGE, ""));
        imageConfig.put("defaultAvatar", getConfigValue(KEY_DEFAULT_AVATAR, ""));
        imageConfig.put("defaultEmptyImage", getConfigValue(KEY_DEFAULT_EMPTY_IMAGE, ""));
        imageConfig.put("defaultOrderImage", getConfigValue(KEY_DEFAULT_ORDER_IMAGE, ""));
        imageConfig.put("orderBackgroundImage", getConfigValue(KEY_ORDER_BACKGROUND_IMAGE, ""));
        return imageConfig;
    }

    @Override
    public void refreshCache() {
        Long tenantId = TenantContext.getTenantId();
        ConcurrentHashMap<String, String> configCache = new ConcurrentHashMap<>();
        List<AppConfig> configs = list();
        for (AppConfig config : configs) {
            if (config.getConfigValue() != null) {
                configCache.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        tenantCaches.put(tenantId, configCache);
        log.info("租户 {} 配置缓存已刷新，共加载 {} 条配置", tenantId, configCache.size());
    }

    /**
     * 转换为BannerVO列表
     */
    private List<BannerVO> convertToBannerVOList(List<Banner> banners) {
        return banners.stream().map(banner -> {
            BannerVO vo = new BannerVO();
            BeanUtils.copyProperties(banner, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
