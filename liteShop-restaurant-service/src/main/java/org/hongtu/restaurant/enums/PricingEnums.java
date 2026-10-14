package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 定价域持久化枚举。数据库与接口只保存/传输 code，枚举名称仅用于后端业务表达。
 */
public final class PricingEnums {
    private PricingEnums() {
    }

    private interface CodedEnum {
        Integer getCode();
    }

    private static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> type, Integer code) {
        if (code == null) return null;
        for (E value : type.getEnumConstants()) {
            if (value.getCode().equals(code)) return value;
        }
        throw new IllegalArgumentException("无效的" + type.getSimpleName() + "编码: " + code);
    }

    @Getter
    public enum CampaignType implements CodedEnum {
        PROMOTION(0), QUANTITY_DISCOUNT(1), ORDER_DISCOUNT(2);
        @EnumValue @JsonValue private final Integer code;
        CampaignType(Integer code) { this.code = code; }
        @JsonCreator public static CampaignType fromCode(Integer code) { return PricingEnums.fromCode(CampaignType.class, code); }
    }

    @Getter
    public enum StackMode implements CodedEnum {
        EXCLUSIVE(0), STACKABLE(1);
        @EnumValue @JsonValue private final Integer code;
        StackMode(Integer code) { this.code = code; }
        @JsonCreator public static StackMode fromCode(Integer code) { return PricingEnums.fromCode(StackMode.class, code); }
    }

    @Getter
    public enum ConditionType implements CodedEnum {
        ALWAYS(0), QUANTITY(1), ORDER_AMOUNT(2);
        @EnumValue @JsonValue private final Integer code;
        ConditionType(Integer code) { this.code = code; }
        @JsonCreator public static ConditionType fromCode(Integer code) { return PricingEnums.fromCode(ConditionType.class, code); }
    }

    @Getter
    public enum GroupingType implements CodedEnum {
        SAME_DISH(0), CATEGORY(1), ANY(2);
        @EnumValue @JsonValue private final Integer code;
        GroupingType(Integer code) { this.code = code; }
        @JsonCreator public static GroupingType fromCode(Integer code) { return PricingEnums.fromCode(GroupingType.class, code); }
    }

    @Getter
    public enum ActionType implements CodedEnum {
        FIXED_PRICE(0), DISCOUNT_RATE(1), REDUCE_AMOUNT(2), FREE_ITEM(3);
        @EnumValue @JsonValue private final Integer code;
        ActionType(Integer code) { this.code = code; }
        @JsonCreator public static ActionType fromCode(Integer code) { return PricingEnums.fromCode(ActionType.class, code); }
    }

    @Getter
    public enum RepeatMode implements CodedEnum {
        ONCE(0), EVERY_GROUP(1);
        @EnumValue @JsonValue private final Integer code;
        RepeatMode(Integer code) { this.code = code; }
        @JsonCreator public static RepeatMode fromCode(Integer code) { return PricingEnums.fromCode(RepeatMode.class, code); }
    }

    @Getter
    public enum TargetType implements CodedEnum {
        ALL(0), DISH(1), CATEGORY(2);
        @EnumValue @JsonValue private final Integer code;
        TargetType(Integer code) { this.code = code; }
        @JsonCreator public static TargetType fromCode(Integer code) { return PricingEnums.fromCode(TargetType.class, code); }
    }

    @Getter
    public enum AudienceType implements CodedEnum {
        ALL(0), MEMBER(1), MEMBER_LEVEL(2);
        @EnumValue @JsonValue private final Integer code;
        AudienceType(Integer code) { this.code = code; }
        @JsonCreator public static AudienceType fromCode(Integer code) { return PricingEnums.fromCode(AudienceType.class, code); }
    }

    @Getter
    public enum AdjustmentType implements CodedEnum {
        MEMBER_PRICE(0), PROMOTION(1), NTH_ITEM_DISCOUNT(2), ORDER_DISCOUNT(3), GIFT(4), MANUAL_DISCOUNT(5);
        @EnumValue @JsonValue private final Integer code;
        AdjustmentType(Integer code) { this.code = code; }
        @JsonCreator public static AdjustmentType fromCode(Integer code) { return PricingEnums.fromCode(AdjustmentType.class, code); }
    }
}
