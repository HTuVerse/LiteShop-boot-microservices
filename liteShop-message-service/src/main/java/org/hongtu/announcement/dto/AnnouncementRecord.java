package org.hongtu.announcement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 公告持久化记录。Mapper 使用显式 SQL，避免平台公告被租户插件错误过滤。 */
@Data
public class AnnouncementRecord {
    private Long id;
    private Long tenantId;
    private String title;
    private String content;
    private String level;
    private String status;
    private Long publisherId;
    private String publisherName;
    private Integer version;
    private Boolean read;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
