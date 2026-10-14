package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 小票纸张宽度及每行可打印字符数。
 */
@Getter
public enum PaperWidthEnum {
    MM_58(0, "58mm", 32),
    MM_80(1, "80mm", 48);

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String label;
    private final int charactersPerLine;

    PaperWidthEnum(Integer code, String label, int charactersPerLine) {
        this.code = code;
        this.label = label;
        this.charactersPerLine = charactersPerLine;
    }

    @JsonCreator
    public static PaperWidthEnum fromCode(Integer code) {
        if (code == null) return null;
        for (PaperWidthEnum value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("无效的纸张宽度编码: " + code);
    }
}
