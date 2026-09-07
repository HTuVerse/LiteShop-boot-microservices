package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 支付状态枚举
 *
 * @author Ht
 * @since 2025-10-27
 */
@Getter
public enum PaymentStatusEnum {

    UNPAID(0, "未支付"),
    PAID(1, "已支付"),
    PARTIAL_REFUND(2, "部分退款"),
    FULL_REFUND(3, "全额退款"),
    TIMEOUT(4, "支付超时"),
    CLOSED(5, "取消支付"),
    FAILED(6, "支付失败");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    PaymentStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static PaymentStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentStatusEnum status : PaymentStatusEnum.values()) {
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
    public static PaymentStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentStatusEnum status : PaymentStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的支付状态码: " + code);
    }
}

