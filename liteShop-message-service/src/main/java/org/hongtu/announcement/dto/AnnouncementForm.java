package org.hongtu.announcement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 管理端公告草稿。发布范围由服务端当前租户上下文再次校验。 */
@Data
public class AnnouncementForm {
    private String title;
    private String content;
    private String level = "NORMAL";
    private String scopeType = "TENANT";
    private List<Long> storeIds = new ArrayList<>();
    private List<AnnouncementAttachment> attachments = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;
}
