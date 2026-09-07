package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 导航显示范围；不代替角色权限或租户鉴权。 */
@Getter
public enum MenuScopeEnum {
    ALL(0, "平台和租户"),
    PLATFORM(1, "仅平台"),
    TENANT(2, "仅租户");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String desc;

    MenuScopeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 接口返回数字；兼容升级期间旧客户端的字符串，非法值直接拒绝。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static MenuScopeEnum fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof String || value instanceof Number) {
            String raw = value.toString();
            for (MenuScopeEnum scope : values()) {
                if (scope.code.toString().equals(raw) || scope.name().equals(raw)) return scope;
            }
        }
        throw new IllegalArgumentException("无效的菜单显示范围，必须为0、1或2");
    }
}
