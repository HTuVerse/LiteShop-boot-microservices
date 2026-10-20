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

    /** 兼容服务间 JSON 的中文描述与数据库编码。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static BooleanEnum fromJson(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        for (BooleanEnum status : BooleanEnum.values()) {
            if (status.getDesc().equals(text) || status.name().equals(text)
                    || status.getCode().toString().equals(text)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的是否状态: " + value);
    }

    public static BooleanEnum fromCode(Integer code) {
        return code == null ? null : fromJson(code);
    }
}
