package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 布尔枚举（用于处理是否类型的字段）
 *
 * @author Ht
 * @since 2025-07-31
 */
@Getter
public enum BooleanEnum {
    
    /**
     * 否
     */
    NO(0, "否"),
    
    /**
     * 是
     */
    YES(1, "是");
    
    @EnumValue
    private final Integer code;
    
    @JsonValue
    private final String desc;
    
    BooleanEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static BooleanEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BooleanEnum status : BooleanEnum.values()) {
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
    public static BooleanEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BooleanEnum status : BooleanEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
} 