package org.hongtu.config.monitor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 从 Nacos 注册实例生成 Prometheus HTTP 服务发现目标。 */
@Service
public class MonitorDiscoveryService {
    private static final String PREFIX = "liteshop-";
    private final DiscoveryClient discoveryClient;
    private final int nodeExporterPort;
    private final String environment;

    public MonitorDiscoveryService(DiscoveryClient discoveryClient,
                                   @Value("${monitor.node-exporter-port:9100}") int nodeExporterPort,
                                   @Value("${monitor.environment:production}") String environment) {
        this.discoveryClient = discoveryClient;
        this.nodeExporterPort = nodeExporterPort;
        this.environment = environment;
    }

    /** 只返回已登记的应用或 exporter；应用所在主机自动派生 node_exporter 目标。 */
    public List<Map<String, Object>> discover(String kind) {
        if (!List.of("application", "host", "mysql", "redis", "rabbitmq").contains(kind)) {
            throw new IllegalArgumentException("不支持的监控目标类型");
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        Set<String> hosts = new LinkedHashSet<>();
        Set<String> registeredNodeHosts = new LinkedHashSet<>();
        for (String name : discoveryClient.getServices()) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalized.startsWith(PREFIX)) continue;
            String exporterKind = exporterKind(normalized);
            boolean application = exporterKind == null &&
                    (normalized.endsWith("-service") || normalized.endsWith("-gateway") || normalized.equals("liteshop-gateway"));
            if (!application && exporterKind == null) continue;
            for (ServiceInstance instance : discoveryClient.getInstances(name)) {
                if (instance.getHost() == null || instance.getHost().isBlank() || instance.getPort() <= 0) continue;
                if (application) hosts.add(instance.getHost());
                if ((application && kind.equals("application")) || kind.equals(exporterKind)) {
                    Map<String, String> labels = new LinkedHashMap<>();
                    labels.put("environment", environment);
                    labels.put("service", normalized);
                    labels.put("host", instance.getHost());
                    if (exporterKind != null) {
                        // 数据库集群名从 exporter 的注册元数据读取，不把连接凭据放入标签。
                        String cluster = instance.getMetadata() == null ? null :
                                instance.getMetadata().get("db_cluster");
                        if (cluster != null && !cluster.isBlank()) labels.put("db_cluster", cluster);
                    }
                    groups.add(group(address(instance.getHost(), instance.getPort()), labels));
                }
                if (exporterKind != null && exporterKind.equals("host")) {
                    registeredNodeHosts.add(instance.getHost());
                }
                if (kind.equals("host") && exporterKind != null) hosts.add(instance.getHost());
            }
        }
        if (kind.equals("host")) {
            for (String host : hosts) {
                if (registeredNodeHosts.contains(host)) continue;
                groups.add(group(address(host, nodeExporterPort),
                        Map.of("environment", environment, "host", host)));
            }
        }
        return groups;
    }

    private static String exporterKind(String service) {
        return switch (service) {
            case "liteshop-mysql-exporter" -> "mysql";
            case "liteshop-redis-exporter" -> "redis";
            case "liteshop-rabbitmq-exporter" -> "rabbitmq";
            case "liteshop-node-exporter" -> "host";
            default -> null;
        };
    }

    private static Map<String, Object> group(String target, Map<String, String> labels) {
        return Map.of("targets", List.of(target), "labels", labels);
    }

    private static String address(String host, int port) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]:" + port : host + ":" + port;
    }
}
