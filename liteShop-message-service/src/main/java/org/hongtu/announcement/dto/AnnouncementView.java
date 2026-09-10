package org.hongtu.announcement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/** 返回给管理端和工作台的公告视图。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementView extends AnnouncementRecord {
    private String scopeType;
    private List<Long> storeIds = new ArrayList<>();
    private List<AnnouncementAttachment> attachments = new ArrayList<>();
}
