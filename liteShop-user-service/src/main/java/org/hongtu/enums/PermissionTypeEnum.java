package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 权限类型枚举
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Getter
public enum PermissionTypeEnum {

    /**
     * 菜单
     */
    MENU(1, "菜单"),

    /**
     * 按钮
     */
    BUTTON(2, "按钮"),

    /**
     * API接口
     */
    API(3, "API");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    PermissionTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 类型码
     * @return 枚举实例
     */
    public static PermissionTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PermissionTypeEnum type : PermissionTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     * */
    @JsonCreator
    public static PermissionTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PermissionTypeEnum type : PermissionTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
} 