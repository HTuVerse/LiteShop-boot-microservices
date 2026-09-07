package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 功能：
 * 作者：
 * 时间：2026/1/30 下午2:15
 */
@Getter
public enum printerBrandEnum {

    /**
     * 飞鹅
     */
    FEIER(1, "飞鹅"),

    /**
     * 其它
     */
    OTHER(2, "其它");

    /**
     * 类型代码（存储在数据库中的值）
     */
    @EnumValue
    private final Integer code;

    /**
     * 类型描述
     */
    @JsonValue
    private final String desc;

    printerBrandEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 对应的枚举，如果不存在则返回null
     */
    public static printerBrandEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (printerBrandEnum type : printerBrandEnum.values()) {
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
    public static printerBrandEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (printerBrandEnum type : printerBrandEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的品牌: " + code);
    }
}
