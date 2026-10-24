package org.hongtu.config.monitor;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证跨主机服务和独立数据库 exporter 的自动发现。 */
class MonitorDiscoveryServiceTest {
    @Test
    void discoversApplicationsAndRegisteredExportersWithoutDuplicateHosts() throws Exception {
        DiscoveryClient client = mock(DiscoveryClient.class);
        NacosDiscoveryProperties nacos = mock(NacosDiscoveryProperties.class);
        NamingService naming = mock(NamingService.class);
        ServiceInstance app = instance("10.0.0.2", 9001, Map.of());
        Instance mysql = exporter("10.0.0.3", 9104, Map.of("db_cluster", "primary"));
        Instance node = exporter("10.0.0.2", 9200, Map.of());
        when(nacos.namingServiceInstance()).thenReturn(naming);
        when(nacos.getGroup()).thenReturn("LITESHOP_GROUP");
        when(client.getServices()).thenReturn(List.of(
                "liteShop-user-service", "liteshop-mysql-exporter", "liteshop-node-exporter"));
        when(client.getInstances("liteShop-user-service"))
                .thenReturn(List.of(app));
        when(naming.getAllInstances("liteshop-mysql-exporter", "LITESHOP_GROUP"))
                .thenReturn(List.of(mysql));
        when(naming.getAllInstances("liteshop-node-exporter", "LITESHOP_GROUP"))
                .thenReturn(List.of(node));

        MonitorDiscoveryService service = new MonitorDiscoveryService(client, nacos, 9100, "production");
        assertEquals(List.of("10.0.0.2:9001"), service.discover("application").getFirst().get("targets"));
        assertEquals(2, service.discover("host").size());
        assertTrue(service.discover("host").stream().anyMatch(group ->
                group.get("targets").equals(List.of("10.0.0.2:9200"))));
        assertTrue(service.discover("host").stream().anyMatch(group ->
                group.get("targets").equals(List.of("10.0.0.3:9100"))));
        assertEquals(Map.of("environment", "production", "service", "liteshop-mysql-exporter",
                "host", "10.0.0.3", "db_cluster", "primary"), service.discover("mysql").getFirst().get("labels"));
    }

    private static ServiceInstance instance(String host, int port, Map<String, String> metadata) {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getHost()).thenReturn(host);
        when(instance.getPort()).thenReturn(port);
        when(instance.getMetadata()).thenReturn(metadata);
        return instance;
    }

    private static Instance exporter(String host, int port, Map<String, String> metadata) {
        Instance instance = new Instance();
        instance.setIp(host);
        instance.setPort(port);
        instance.setMetadata(metadata);
        return instance;
    }
}
