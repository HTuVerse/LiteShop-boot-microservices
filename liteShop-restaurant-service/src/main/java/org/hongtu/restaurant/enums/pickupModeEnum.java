package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 功能：
 * 作者：
 * 时间：2025/11/19 下午11:31
 */

@Getter
public enum pickupModeEnum {

    PUTONTABLE(0, "送上桌"),
    NUMBERCALLING(1, "叫号自取");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    pickupModeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static pickupModeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (pickupModeEnum mode : pickupModeEnum.values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static pickupModeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (pickupModeEnum mode : pickupModeEnum.values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("无效的取餐模式码: " + code);
    }

}
