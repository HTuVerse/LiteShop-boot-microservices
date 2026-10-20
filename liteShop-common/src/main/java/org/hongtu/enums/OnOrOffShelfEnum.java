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

    /** 兼容服务间 JSON 的中文描述与数据库编码。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static OnOrOffShelfEnum fromJson(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        for (OnOrOffShelfEnum status : OnOrOffShelfEnum.values()) {
            if (status.getDesc().equals(text) || status.name().equals(text)
                    || status.getCode().toString().equals(text)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的上下架状态: " + value);
    }

    public static OnOrOffShelfEnum fromCode(Integer code) {
        return code == null ? null : fromJson(code);
    }
}
