package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 商品状态枚举
 *
 * @author Ht
 * @since 2025-07-31
 */
@Getter
public enum OnOrOffShelfEnum {
    
    /**
     * 下架
     */
    OFF_SHELF(0, "下架"),
    
    /**
     * 上架
     */
    ON_SHELF(1, "上架");
    
    @EnumValue
    private final Integer code;
    
    @JsonValue
    private final String desc;
    
    OnOrOffShelfEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static OnOrOffShelfEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OnOrOffShelfEnum status : OnOrOffShelfEnum.values()) {
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
    public static OnOrOffShelfEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OnOrOffShelfEnum status : OnOrOffShelfEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
} 