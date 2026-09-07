package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 积分操作枚举
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Getter
public enum PointsOperationEnum {

    /**
     * 增加积分
     */
    INCREASE(1, "增加积分"),

    /**
     * 减少积分
     */
    DECREASE(2, "减少积分");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    PointsOperationEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 操作码
     * @return 枚举实例
     */
    public static PointsOperationEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PointsOperationEnum operation : PointsOperationEnum.values()) {
            if (operation.getCode().equals(code)) {
                return operation;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     * */
    @JsonCreator
    public static PointsOperationEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PointsOperationEnum operation : PointsOperationEnum.values()) {
            if (operation.getCode().equals(code)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
} 