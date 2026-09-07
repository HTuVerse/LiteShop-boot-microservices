package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 功能：
 * 作者：
 * 时间：2025/8/7 上午1:26
 */
@Getter
public enum CouponMsgOperateTypeEnum {
    /**
     * 发放
     */
    SEND_OF_MSG(0, "发放"),

    /**
     * 过期
     */
    OUT_OF_DATE_MSG(1, "过期");

    @EnumValue
    private final Integer code;

    @JsonValue  // 序列化时使用 desc 值（返回前端显示），反序列化时使用 code（@JsonCreator）
    private final String desc;

    CouponMsgOperateTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static CouponMsgOperateTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CouponMsgOperateTypeEnum type : CouponMsgOperateTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Jackson 反序列化方法
     * 支持从 code(Integer) 或 desc(String) 反序列化
     * */
    @JsonCreator
    public static CouponMsgOperateTypeEnum fromValue(Object value) {
        if (value == null) {
            return null;
        }

        // 如果是 Integer 类型，按 code 匹配
        if (value instanceof Integer) {
            Integer code = (Integer) value;
            for (CouponMsgOperateTypeEnum type : CouponMsgOperateTypeEnum.values()) {
                if (type.getCode().equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("无效的优惠券操作类型码: " + code);
        }

        // 如果是 String 类型，按 desc 匹配
        if (value instanceof String) {
            String desc = (String) value;
            for (CouponMsgOperateTypeEnum type : CouponMsgOperateTypeEnum.values()) {
                if (type.getDesc().equals(desc)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("无效的优惠券操作类型描述: " + desc);
        }

        throw new IllegalArgumentException("无效的优惠券操作类型值: " + value);
    }
}
