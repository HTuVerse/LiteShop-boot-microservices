package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 功能：
 * 作者：
 * 时间：2025/8/7 上午12:52
 */
@Getter
public enum VerifyStatusEnum {
    /**
     * 未核销
     */
    NOT_VERIFY(0, "未核销"),

    /**
     * 已核销
     */
    VERIFYED(1, "已核销");


    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    VerifyStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static VerifyStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (VerifyStatusEnum type : VerifyStatusEnum.values()) {
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
    public static VerifyStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (VerifyStatusEnum type : VerifyStatusEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
}
