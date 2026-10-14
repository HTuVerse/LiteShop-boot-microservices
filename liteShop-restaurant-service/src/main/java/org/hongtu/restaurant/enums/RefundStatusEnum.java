package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 退款单状态。渠道确认成功后才允许提交订单和库存。
 */
@Getter
public enum RefundStatusEnum {
    REQUESTED(0, "已申请"),
    PROCESSING(1, "处理中"),
    SUCCEEDED(2, "成功"),
    FAILED(3, "失败");

    @EnumValue
    private final Integer code;
    @JsonValue
    private final String desc;

    RefundStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
