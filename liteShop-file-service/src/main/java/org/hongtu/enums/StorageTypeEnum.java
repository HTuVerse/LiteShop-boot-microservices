package org.hongtu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 文件存储类型。
 */
@Getter
public enum StorageTypeEnum {
    LOCAL(0, "本地存储"),
    QINIU(1, "七牛云历史存储"),
    MINIO(2, "MinIO"),
    OSS(3, "阿里云OSS");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String description;

    StorageTypeEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static StorageTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (StorageTypeEnum value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("无效的文件存储类型编码: " + code);
    }
}
