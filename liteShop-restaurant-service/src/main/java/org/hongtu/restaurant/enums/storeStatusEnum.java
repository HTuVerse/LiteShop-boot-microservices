package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 功能：
 * 作者：HT
 * 时间：2025/11/5 下午4:02
 */
@Getter
public enum storeStatusEnum {

    STOPBUSINESS( 0,"停业"),
    ONBUSINESS(1, "营业中"),
    ONREST(2, "休息中");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    storeStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static storeStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (storeStatusEnum mode : storeStatusEnum.values()) {
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
    public static storeStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (storeStatusEnum mode : storeStatusEnum.values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("无效的支付模式码: " + code);
    }
}
