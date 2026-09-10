package org.hongtu.announcement.dto;

import lombok.Data;

/** 公告附件元数据，实际文件由文件服务负责存储。 */
@Data
public class AnnouncementAttachment {
    private Long id;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private Integer sortOrder;
}
