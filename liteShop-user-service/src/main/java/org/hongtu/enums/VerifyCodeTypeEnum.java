package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 验证码类型枚举
 *
 * @author Honghong
 * @since 2025-02-24
 */
@Getter
public enum VerifyCodeTypeEnum {

    /**
     * 登录或注册验证码（自动判断用户是否存在，存在则登录，不存在则注册）
     */
    LOGIN_OR_REGISTER(1, "login_or_register", "登录/注册"),

    /**
     * 重置密码验证码
     */
    RESET(2, "reset", "重置密码");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String type;

    private final String desc;

    VerifyCodeTypeEnum(Integer code, String type, String desc) {
        this.code = code;
        this.type = type;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 类型码
     * @return 枚举实例
     */
    public static VerifyCodeTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (VerifyCodeTypeEnum typeEnum : VerifyCodeTypeEnum.values()) {
            if (typeEnum.getCode().equals(code)) {
                return typeEnum;
            }
        }
        return null;
    }

    /**
     * 根据type字符串获取枚举
     *
     * @param type 类型字符串
     * @return 枚举实例
     */
    public static VerifyCodeTypeEnum getByType(String type) {
        if (type == null) {
            return null;
        }
        for (VerifyCodeTypeEnum typeEnum : VerifyCodeTypeEnum.values()) {
            if (typeEnum.getType().equals(type)) {
                return typeEnum;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static VerifyCodeTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (VerifyCodeTypeEnum typeEnum : VerifyCodeTypeEnum.values()) {
            if (typeEnum.getCode().equals(code)) {
                return typeEnum;
            }
        }
        throw new IllegalArgumentException("无效的验证码类型码: " + code);
    }
}
