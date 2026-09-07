package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 支付模式枚举
 *
 * @author Ht
 * @since 2025-10-27
 */
@Getter
public enum PaymentModeEnum {

    ONLINE(1, "线上支付"),
    OFFLINE(2, "线下支付");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    PaymentModeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static PaymentModeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentModeEnum mode : PaymentModeEnum.values()) {
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
    public static PaymentModeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentModeEnum mode : PaymentModeEnum.values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("无效的支付模式码: " + code);
    }
}

