package org.hongtu.restaurant.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 小票纸张宽度及安全排版字节数；各留 2 字节边距，防止设备自动折行。
 */
@Getter
public enum PaperWidthEnum {
    MM_58(0, "58mm", 30),
    MM_80(1, "80mm", 46);

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
