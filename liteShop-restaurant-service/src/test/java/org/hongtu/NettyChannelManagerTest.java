package org.hongtu;

import io.netty.channel.embedded.EmbeddedChannel;
import org.hongtu.restaurant.netty.NettyChannelManager;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyChannelManagerTest {

    private final NettyChannelManager manager = new NettyChannelManager(new ObjectMapper());

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldIsolateSameSessionNumberAcrossTenants() {
        EmbeddedChannel tenantOneChannel = new EmbeddedChannel();
        EmbeddedChannel tenantTwoChannel = new EmbeddedChannel();
        manager.addChannel(1L, "SAME-SESSION", tenantOneChannel);
        manager.addChannel(2L, "SAME-SESSION", tenantTwoChannel);

        TenantContext.setTenantId(1L);
        assertEquals(1, manager.getChannelCount("SAME-SESSION"));

        TenantContext.setTenantId(2L);
        assertEquals(1, manager.getChannelCount("SAME-SESSION"));
        assertTrue(manager.hasOnlineUsers("SAME-SESSION"));
    }
}
