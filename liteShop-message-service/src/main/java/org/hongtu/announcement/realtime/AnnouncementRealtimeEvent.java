package org.hongtu.announcement.realtime;

import java.util.List;

/** SSE 只传变更提示，公告正文仍从数据库接口读取。 */
public record AnnouncementRealtimeEvent(
        String action,
        Long announcementId,
        Long tenantId,
        List<Long> storeIds,
        int version
) {
    public AnnouncementRealtimeEvent {
        storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
    }
}
