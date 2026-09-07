package org.hongtu.config.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.config.DTO.AppConfig;
import org.hongtu.config.VO.AppConfigVO;
import org.hongtu.config.enums.ConfigGroupEnum;

import java.util.List;
import java.util.Map;

/**
 * 系统配置 Service 接口
 *
 * @author Ht
 */
public interface AppConfigService extends IService<AppConfig> {

    /**
     * 根据配置键获取配置值
     *
     * @param configKey 配置键
     * @return 配置值
     */
    String getConfigValue(String configKey);

    /**
     * 根据配置键获取配置值（带默认值）
     *
     * @param configKey    配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    String getConfigValue(String configKey, String defaultValue);

    /**
     * 根据配置键获取配置对象
     *
     * @param configKey 配置键
     * @return 配置对象
     */
    AppConfig getByConfigKey(String configKey);

    /**
     * 根据分组获取配置列表
     *
     * @param configGroup 配置分组
     * @return 配置列表
     */
    List<AppConfig> getByConfigGroup(ConfigGroupEnum configGroup);

    /**
     * 批量获取配置（根据多个key）
     *
     * @param configKeys 配置键列表
     * @return 配置Map
     */
    Map<String, String> getConfigsByKeys(List<String> configKeys);

    /**
     * 更新配置值
     *
     * @param configKey   配置键
     * @param configValue 配置值
     * @return 是否成功
     */
    boolean updateConfigValue(String configKey, String configValue);

    /**
     * 获取小程序端所有配置（用于前端初始化）
     *
     * @return 配置VO
     */
    AppConfigVO getMiniappConfig();

    /**
     * 获取默认图片配置
     *
     * @return 默认图片配置Map
     */
    Map<String, String> getDefaultImageConfig();

    /**
     * 刷新配置缓存
     */
    void refreshCache();
}
