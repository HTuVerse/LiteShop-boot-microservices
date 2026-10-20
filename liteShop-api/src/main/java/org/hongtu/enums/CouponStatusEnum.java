package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 优惠券状态枚举
 *
 * @author Ht
 * @since 2025-07-31
 */
@Getter
public enum CouponStatusEnum {

    /**
     * 未生效
     */
    NOT_EFFECTIVE(0, "未生效"),

    /**
     * 未使用
     */
    NOT_USE(1, "未使用"),

    /**
     * 已使用
     */
    USED(2, "已使用"),
    /**
     * 已过期
     */
    OUT_OF_DATE(3, "已过期");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    CouponStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static CouponStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CouponStatusEnum type : CouponStatusEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /** 兼容服务间 JSON 的中文描述与请求中的数字编码。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CouponStatusEnum fromJson(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        for (CouponStatusEnum type : CouponStatusEnum.values()) {
            if (type.getDesc().equals(text) || type.name().equals(text)
                    || type.getCode().toString().equals(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的优惠券状态: " + value);
    }

    public static CouponStatusEnum fromCode(Integer code) {
        return code == null ? null : fromJson(code);
    }
}
