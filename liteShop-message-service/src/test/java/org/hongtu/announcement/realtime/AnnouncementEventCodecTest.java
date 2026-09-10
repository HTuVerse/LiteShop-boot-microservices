package org.hongtu.announcement.realtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnnouncementEventCodecTest {
    @Test
    void roundTripsTenantAndStoreScope() {
        AnnouncementRealtimeEvent source = new AnnouncementRealtimeEvent(
                "PUBLISHED", 10001L, 20001L, List.of(30001L, 30002L), 4);

        AnnouncementRealtimeEvent decoded = AnnouncementEventCodec.decode(AnnouncementEventCodec.encode(source));

        assertEquals(source, decoded);
    }

    @Test
    void roundTripsPlatformScope() {
        AnnouncementRealtimeEvent decoded = AnnouncementEventCodec.decode(
                AnnouncementEventCodec.encode(new AnnouncementRealtimeEvent(
                        "WITHDRAWN", 10001L, null, List.of(), 5)));

        assertNull(decoded.tenantId());
        assertEquals(List.of(), decoded.storeIds());
        assertEquals("WITHDRAWN", decoded.action());
    }
}
