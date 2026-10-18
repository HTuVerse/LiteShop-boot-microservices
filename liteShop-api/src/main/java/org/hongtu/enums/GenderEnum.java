package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 用户性别枚举
 * 
 * @author Honghong
 * @since 2025-05-06
 */
@Getter
public enum GenderEnum {
    /**
     * 保密
     */
    SECRET(0, "保密"),

    /**
     * 男性
     */
    MALE(1, "男"),

    /**
     * 女性
     */
    FEMALE(2, "女");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    GenderEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 性别码
     * @return 枚举实例
     */
    public static GenderEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (GenderEnum gender : GenderEnum.values()) {
            if (gender.getCode().equals(code)) {
                return gender;
            }
        }
        return null;
    }

    /** 兼容接口返回的中文描述和请求传入的数字编码。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static GenderEnum fromJson(Object value) {
        if (value == null) {
            return null;
        }
        for (GenderEnum gender : GenderEnum.values()) {
            if (gender.getDesc().equals(value.toString())
                    || gender.getCode().toString().equals(value.toString())) {
                return gender;
            }
        }
        throw new IllegalArgumentException("无效的性别值: " + value);
    }
}
