package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 支付方式枚举
 *
 * @author Ht
 * @since 2025-10-27
 */
@Getter
public enum PaymentTypeEnum {

    WECHAT(1, "微信"),
    ALIPAY(2, "支付宝"),
    CASH(3, "现金"),
    BANK_CARD(4, "银行卡"),
    OFFLINE_PAY(5,"线下支付"),
    BANLANCE_PAY(6,"余额支付"),
    COUPON_PAY(7,"优惠券全额抵扣");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    PaymentTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static PaymentTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentTypeEnum type : PaymentTypeEnum.values()) {
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
    public static PaymentTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentTypeEnum type : PaymentTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的支付方式码: " + code);
    }
}

