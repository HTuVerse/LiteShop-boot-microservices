package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 用户状态枚举
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Getter
public enum StatusEnum {

    /**
     * 禁用
     */
    DISABLED(0, "禁用"),

    /**
     * 正常
     */
    NORMAL(1, "正常");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    StatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static StatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (StatusEnum status : StatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }


    /** 兼容接口返回的中文描述和请求传入的数字编码。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static StatusEnum fromJson(Object value) {
        if (value == null) {
            return null;
        }
        for (StatusEnum status : StatusEnum.values()) {
            if (status.getDesc().equals(value.toString())
                    || status.getCode().toString().equals(value.toString())) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的用户状态值: " + value);
    }
}
