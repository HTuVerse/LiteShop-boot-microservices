package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 功能：
 * 作者：
 * 时间：2025/9/22 下午11:48
 */
@Getter
public enum SceneTypeEnum {

    RESTAURANT(0, "在线点餐"),
    TAKE_OUT(1, "预点餐"),
    DELIVERY(2, "门店自提"),
    ADMIN_ORDER(3,"管理员下单");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    SceneTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static SceneTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SceneTypeEnum status : SceneTypeEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     * */
    @JsonCreator
    public static SceneTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SceneTypeEnum status : SceneTypeEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
}
