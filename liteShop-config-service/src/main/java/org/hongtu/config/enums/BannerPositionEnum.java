package org.hongtu.config.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 轮播图位置枚举
 *
 * @author Ht
 */
@Getter
public enum BannerPositionEnum {

    /**
     * 首页轮播图
     */
    HOME_BANNER(1, "首页轮播图"),

    /**
     * 点餐页轮播图
     */
    ORDER_BANNER(2, "点餐页轮播图"),

    /**
     * 个人中心轮播图
     */
    USER_BANNER(3, "个人中心轮播图"),

    /**
     * 活动页轮播图
     */
    ACTIVITY_BANNER(4, "活动页轮播图");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    BannerPositionEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 位置码
     * @return 枚举实例
     */
    public static BannerPositionEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BannerPositionEnum position : BannerPositionEnum.values()) {
            if (position.getCode().equals(code)) {
                return position;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static BannerPositionEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BannerPositionEnum position : BannerPositionEnum.values()) {
            if (position.getCode().equals(code)) {
                return position;
            }
        }
        throw new IllegalArgumentException("无效的轮播图位置码: " + code);
    }
}
