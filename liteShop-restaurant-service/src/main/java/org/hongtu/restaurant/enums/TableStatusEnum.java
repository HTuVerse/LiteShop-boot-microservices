package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 桌位状态枚举
 *
 * @author Ht
 * @since 2025-11-05
 */
@Getter
public enum TableStatusEnum {

    IDLE(0, "空闲"),
    IN_USE(1, "使用中"),
    RESERVED(2, "预留"),
    DISABLED(3, "停用");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    TableStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static TableStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TableStatusEnum status : TableStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static TableStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TableStatusEnum status : TableStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的桌位状态码: " + code);
    }
}


