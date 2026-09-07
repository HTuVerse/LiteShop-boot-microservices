package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 积分来源枚举
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Getter
public enum PointsSourceEnum {

    /**
     * 注册奖励
     */
    REGISTER(1, "注册奖励"),

    /**
     * 签到奖励
     */
    SIGN_IN(2, "签到奖励"),

    /**
     * 购物奖励
     */
    SHOPPING(3, "购物奖励"),

    /**
     * 活动奖励
     */
    ACTIVITY(4, "活动奖励"),

    /**
     * 积分兑换
     */
    EXCHANGE(5, "积分兑换"),


    /**
     * 积分兑换
     */
    OTHERS(6, "其它");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    PointsSourceEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 来源码
     * @return 枚举实例
     */
    public static PointsSourceEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PointsSourceEnum source : PointsSourceEnum.values()) {
            if (source.getCode().equals(code)) {
                return source;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     * */
    @JsonCreator
    public static PointsSourceEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PointsSourceEnum source : PointsSourceEnum.values()) {
            if (source.getCode().equals(code)) {
                return source;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
} 