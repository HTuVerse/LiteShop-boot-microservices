package org.hongtu.config.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 配置类型枚举
 *
 * @author Ht
 */
@Getter
public enum ConfigTypeEnum {

    /**
     * 图片
     */
    IMAGE(1, "图片"),

    /**
     * 文本
     */
    TEXT(2, "文本"),

    /**
     * JSON
     */
    JSON(3, "JSON"),

    /**
     * 颜色
     */
    COLOR(4, "颜色"),

    /**
     * 数字
     */
    NUMBER(5, "数字");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    ConfigTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 类型码
     * @return 枚举实例
     */
    public static ConfigTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ConfigTypeEnum type : ConfigTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static ConfigTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ConfigTypeEnum type : ConfigTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的配置类型码: " + code);
    }
}
