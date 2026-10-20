package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 优惠券类型枚举
 * <p>
 * 支持的优惠券类型：
 * <ul>
 *   <li>满减券(0)：满指定金额减固定金额，如满100减20</li>
 *   <li>整单折扣券(1)：整单按比例打折，如整单85折（value=0.85）</li>
 *   <li>菜品免单券(2)：指定菜品中任选一份免费</li>
 *   <li>菜品折扣券(3)：指定菜品中任选一份打折</li>
 *   <li>代金券(4)：固定面值直接抵扣，可选指定菜品</li>
 * </ul>
 * 
 * 计算优先级：菜品免单券 > 菜品折扣券 > 代金券 > 满减券 > 整单折扣券
 * </p>
 *
 * @author Ht
 * @since 2025-07-31
 */
@Getter
public enum CouponTypeEnum {
    
    /**
     * 满减券
     * <p>满指定金额减固定金额，如满100减20</p>
     * <p>value 字段表示减免金额（元）</p>
     */
    FULL_REDUCTION(0, "满减券"),

    /**
     * 整单折扣券
     * <p>整单享受折扣，如整单85折</p>
     * <p>value 字段表示折扣率（0.85表示打85折）</p>
     */
    ORDER_DISCOUNT(1, "整单折扣券"),

    /**
     * 菜品免单券
     * <p>指定菜品免费，需要通过 coupon_dish_item 表关联菜品</p>
     * <p>value 字段不使用</p>
     */
    DISH_FREE(2, "菜品免单券"),

    /** 指定菜品中任选一份按折扣率优惠。 */
    DISH_DISCOUNT(3, "菜品折扣券"),

    /** 固定面值直接抵扣，可选指定菜品，不设置最低消费即无门槛。 */
    CASH_VOUCHER(4, "代金券");
    
    @EnumValue
    private final Integer code;
    
    @JsonValue
    private final String desc;
    
    CouponTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    /**
     * 根据code获取枚举
     *
     * @param code 状态码
     * @return 枚举实例
     */
    public static CouponTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CouponTypeEnum type : CouponTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /** 兼容服务间 JSON 的中文描述与请求中的数字编码。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CouponTypeEnum fromJson(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        for (CouponTypeEnum type : CouponTypeEnum.values()) {
            if (type.getDesc().equals(text) || type.name().equals(text)
                    || type.getCode().toString().equals(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的优惠券类型: " + value);
    }

    public static CouponTypeEnum fromCode(Integer code) {
        return code == null ? null : fromJson(code);
    }

    /**
     * 判断该类型是否需要关联菜品
     * @return true 表示需要关联菜品
     */
    public boolean needDishItems() {
        return this == DISH_FREE || this == DISH_DISCOUNT;
    }

    /**
     * 判断该类型是否需要设置 value 字段
     * @return true 表示需要设置 value
     */
    public boolean needValue() {
        return this == FULL_REDUCTION || this == ORDER_DISCOUNT || this == DISH_DISCOUNT || this == CASH_VOUCHER;
    }
} 
