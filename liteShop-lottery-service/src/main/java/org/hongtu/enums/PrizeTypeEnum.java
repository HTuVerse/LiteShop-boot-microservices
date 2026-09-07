package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 优惠券类型枚举
 *
 * @author Ht
 * @since 2025-07-31
 */
@Getter
public enum PrizeTypeEnum {

    /**
     * 优惠券
     */
    COUPON(0, "优惠券"),
    
    /**
     * 积分
     */
    POINTS(1, "积分"),
    
    /**
     * 实物
     */
    REALITY(2, "实物"),

    /**
     * 谢谢参与
     */
    THANK_YOU_PARTICIPATION(3, "谢谢参与");
    
    @EnumValue
    private final Integer code;
    
    @JsonValue
    private final String desc;
    
    PrizeTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static PrizeTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PrizeTypeEnum type : PrizeTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * 告诉 Jackson 根据 code 字段反序列化
     * */
    @JsonCreator
    public static PrizeTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PrizeTypeEnum type : PrizeTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
} 