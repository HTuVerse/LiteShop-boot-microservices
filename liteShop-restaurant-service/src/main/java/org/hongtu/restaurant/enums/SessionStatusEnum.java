package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 会话状态枚举
 *
 * @author Ht
 * @since 2025-11-05
 */
@Getter
public enum SessionStatusEnum {

    IN_PROGRESS(1, "进行中"),
    ENDED(2, "已结束");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    SessionStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static SessionStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SessionStatusEnum status : SessionStatusEnum.values()) {
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
    public static SessionStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SessionStatusEnum status : SessionStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的会话状态码: " + code);
    }
}

