package org.hongtu.config.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 配置分组枚举
 *
 * @author Ht
 */
@Getter
public enum ConfigGroupEnum {

    /**
     * 默认图片配置
     */
    DEFAULT_IMAGE(1, "默认图片配置"),

    /**
     * 应用信息配置
     */
    APP_INFO(2, "应用信息配置"),

    /**
     * 主题配置
     */
    THEME(3, "主题配置"),

    /**
     * 联系方式配置
     */
    CONTACT(4, "联系方式配置"),

    /**
     * 其他配置
     */
    OTHER(5, "其他配置"),

    /**
     * 部署配置
     */
    DEPLOY(6, "部署配置");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    ConfigGroupEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 分组码
     * @return 枚举实例
     */
    public static ConfigGroupEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ConfigGroupEnum group : ConfigGroupEnum.values()) {
            if (group.getCode().equals(code)) {
                return group;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static ConfigGroupEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ConfigGroupEnum group : ConfigGroupEnum.values()) {
            if (group.getCode().equals(code)) {
                return group;
            }
        }
        throw new IllegalArgumentException("无效的配置分组码: " + code);
    }
}
