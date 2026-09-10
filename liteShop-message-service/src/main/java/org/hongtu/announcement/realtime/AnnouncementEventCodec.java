package org.hongtu.announcement.realtime;

import java.util.Arrays;
import java.util.List;

/** 仅编码固定枚举和数字，避免 Redis Pub/Sub 绑定 Java 序列化格式。 */
final class AnnouncementEventCodec {
    private AnnouncementEventCodec() {
    }

    static String encode(AnnouncementRealtimeEvent event) {
        String stores = event.storeIds().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return event.action() + "|" + event.announcementId() + "|"
                + (event.tenantId() == null ? "" : event.tenantId()) + "|" + stores + "|" + event.version();
    }

    static AnnouncementRealtimeEvent decode(String value) {
        String[] fields = value.split("\\|", -1);
        if (fields.length != 5) throw new IllegalArgumentException("无效的公告实时事件");
        List<Long> stores = fields[3].isBlank() ? List.of()
                : Arrays.stream(fields[3].split(",")).map(Long::valueOf).toList();
        return new AnnouncementRealtimeEvent(fields[0], Long.valueOf(fields[1]),
                fields[2].isBlank() ? null : Long.valueOf(fields[2]), stores, Integer.parseInt(fields[4]));
    }
}
