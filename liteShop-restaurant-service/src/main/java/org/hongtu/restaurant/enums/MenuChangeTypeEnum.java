package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 菜单变更业务类型。
 */
@Getter
public enum MenuChangeTypeEnum {
    STORE_CONFIG(0, "门店菜品配置");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String description;

    MenuChangeTypeEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static MenuChangeTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (MenuChangeTypeEnum value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("无效的菜单变更类型编码: " + code);
    }
}
