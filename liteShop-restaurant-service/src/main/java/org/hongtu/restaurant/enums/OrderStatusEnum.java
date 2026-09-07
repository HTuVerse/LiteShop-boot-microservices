package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 订单状态枚举
 *
 * @author Ht
 * @since 2025-10-27
 */
@Getter
public enum OrderStatusEnum {

    NOPAID(0, "未支付"),
    PLACED(1, "已下单"),
    ACCEPTED(2, "已接单"),
    PREPARING(3, "制作中"),
    PREPARED(4, "制作完成"),
    COMPLETED(5, "已完成"),
    CANCELLED(6, "已取消");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    OrderStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static OrderStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum status : OrderStatusEnum.values()) {
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
    public static OrderStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum status : OrderStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的订单状态码: " + code);
    }
}

