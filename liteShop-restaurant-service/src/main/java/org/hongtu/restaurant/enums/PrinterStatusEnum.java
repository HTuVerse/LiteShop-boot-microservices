package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 打印机状态枚举
 * 
 * @author Ht
 * @since 2025-01-26
 */
@Getter
public enum PrinterStatusEnum {
    
    /**
     * 离线
     */
    OFFLINE(0, "离线"),
    
    /**
     * 在线
     */
    ONLINE(1, "在线"),
    
    /**
     * 缺纸
     */
    OUT_OF_PAPER(2, "缺纸");

    /**
     * 状态代码（存储在数据库中的值）
     */
    @EnumValue
    private final Integer code;
    
    /**
     * 状态描述
     */
    @JsonValue
    private final String desc;

    PrinterStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据代码获取枚举
     * 
     * @param code 状态代码
     * @return 对应的枚举，如果不存在则返回null
     */
    public static PrinterStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PrinterStatusEnum status : PrinterStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }


    /**
     * 告诉 Jackson 根据 code 字段反序列化
     */
    @JsonCreator
    public static PrinterStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PrinterStatusEnum status : PrinterStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的状态码: " + code);
    }
}
