package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 打印机类型枚举
 * 
 * @author Ht
 * @since 2025-01-26
 */
@Getter
public enum PrinterTypeEnum {
    
    /**
     * 小票机
     */
    RECEIPT(1, "小票机"),
    
    /**
     * 标签机
     */
    LABEL(2, "标签机");
    
    /**
     * 类型代码（存储在数据库中的值）
     */
    @EnumValue
    private final Integer code;
    
    /**
     * 类型描述
     */
    @JsonValue
    private final String desc;

    PrinterTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据代码获取枚举
     * 
     * @param code 类型代码
     * @return 对应的枚举，如果不存在则返回null
     */
    public static PrinterTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PrinterTypeEnum type : PrinterTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static PrinterTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PrinterTypeEnum type : PrinterTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的类型: " + code);
    }
}
