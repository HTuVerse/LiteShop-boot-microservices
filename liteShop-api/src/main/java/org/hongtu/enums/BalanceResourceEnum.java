package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 功能：
 * 作者：
 * 时间：2025/8/25 下午4:30
 */
@Getter
public enum BalanceResourceEnum {
    /**
     * 用户充值
     */
    USER_RECHARGE(1, "用户充值"),

    /**
     * 管理员充值
     */
    ADMIN_RECHARGE(2, "管理员充值"),

    /**
     * 退款
     */
    REFUND(3, "退款"),

    /**
     * 消费
     * */
    EXPENSE(4, "消费");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    BalanceResourceEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 性别码
     * @return 枚举实例
     */
    public static BalanceResourceEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BalanceResourceEnum balanceResource : BalanceResourceEnum.values()) {
            if (balanceResource.getCode().equals(code)) {
                return balanceResource;
            }
        }
        return null;
    }
    
    /**
     * 告诉 Jackson 根据 code 字段反序列化
     * */
    @JsonCreator
    public static BalanceResourceEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BalanceResourceEnum balanceResource : BalanceResourceEnum.values()) {
            if (balanceResource.getCode().equals(code)) {
                return balanceResource;
            }
        }
        throw new IllegalArgumentException("无效的权限类型码: " + code);
    }
}
