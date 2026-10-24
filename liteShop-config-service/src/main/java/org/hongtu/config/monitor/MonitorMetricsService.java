package org.hongtu.config.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询 Prometheus 并向平台看板提供受控的指标数据。 */
@Service
public class MonitorMetricsService {
    private static final List<String> KINDS = List.of("application", "host", "mysql", "redis", "rabbitmq");
    private static final List<String> EXPECTED_SERVICES = List.of(
            "liteshop-gateway", "liteshop-user-service", "liteshop-coupon-service",
            "liteshop-mall-service", "liteshop-lottery-service", "liteshop-restaurant-service",
            "liteshop-pay-service", "liteshop-message-service", "liteshop-file-service",
            "liteshop-config-service", "liteshop-ai-service");
    private final MonitorDiscoveryService discovery;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;

    public MonitorMetricsService(MonitorDiscoveryService discovery, ObjectMapper objectMapper,
                                 @Value("${monitor.prometheus.base-url:}") String baseUrl) {
        this.discovery = discovery;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** 返回所有已发现目标和最近一次抓取状态。 */
    public List<Map<String, Object>> targets() {
        Map<String, Boolean> state = new HashMap<>();
        JsonNode samples = prometheus("/api/v1/query?query=" + encode("up{job=~\"liteshop-.*\"}"))
                .path("data").path("result");
        for (JsonNode sample : samples) {
            String key = sample.path("metric").path("job").asText() + "|" +
                    sample.path("metric").path("instance").asText();
            state.put(key, sample.path("value").path(1).asDouble(0) == 1);
        }
        List<Map<String, Object>> targets = new ArrayList<>();
        for (String kind : KINDS) {
            String job = job(kind);
            for (Map<String, Object> group : discovery.discover(kind)) {
                @SuppressWarnings("unchecked")
                List<String> addresses = (List<String>) group.get("targets");
                @SuppressWarnings("unchecked")
                Map<String, String> labels = (Map<String, String>) group.get("labels");
                String instance = addresses.getFirst();
                Map<String, Object> target = new LinkedHashMap<>();
                target.put("kind", kind);
                target.put("instance", instance);
                target.put("host", labels.getOrDefault("host", ""));
                target.put("service", labels.getOrDefault("service", ""));
                target.put("dbCluster", labels.getOrDefault("db_cluster", ""));
                target.put("up", state.get(job + "|" + instance));
                targets.add(target);
            }
        }
        // Nacos 会移除离线的临时实例；仍需显示预期服务缺席，不能把消失误判为全部正常。
        for (String service : EXPECTED_SERVICES) {
            boolean present = targets.stream().anyMatch(target ->
                    service.equalsIgnoreCase(String.valueOf(target.get("service"))));
            if (!present) {
                Map<String, Object> missing = new LinkedHashMap<>();
                missing.put("kind", "application");
                missing.put("instance", "");
                missing.put("host", "");
                missing.put("service", service);
                missing.put("dbCluster", "");
                missing.put("up", false);
                targets.add(missing);
            }
        }
        return targets;
    }

    /** 汇总在线目标和当前告警数量。 */
    public Map<String, Object> overview() {
        List<Map<String, Object>> targets = targets();
        long up = targets.stream().filter(target -> Boolean.TRUE.equals(target.get("up"))).count();
        long down = targets.stream().filter(target -> Boolean.FALSE.equals(target.get("up"))).count();
        return Map.of("total", targets.size(), "up", up, "down", down,
                "pending", targets.size() - up - down, "alertCount", alerts().size());
    }

    /** 只允许预定义指标和已发现实例，避免任意 PromQL 查询。 */
    public Map<String, Object> trend(String kind, String instance, String metric, int rangeMinutes) {
        if (!KINDS.contains(kind) || rangeMinutes < 15 || rangeMinutes > 1440) {
            throw new IllegalArgumentException("无效的目标类型或时间范围");
        }
        boolean known = discovery.discover(kind).stream().anyMatch(group -> {
            @SuppressWarnings("unchecked")
            List<String> addresses = (List<String>) group.get("targets");
            return addresses.contains(instance);
        });
        if (!known) throw new IllegalArgumentException("目标不存在或未登记");
        String expression = expression(kind, instance, metric);
        long end = System.currentTimeMillis() / 1000;
        long start = end - rangeMinutes * 60L;
        long step = Math.max(15, rangeMinutes * 60L / 120);
        String path = "/api/v1/query_range?query=" + encode(expression) + "&start=" + start +
                "&end=" + end + "&step=" + step;
        JsonNode series = prometheus(path).path("data").path("result");
        List<Map<String, Object>> points = new ArrayList<>();
        if (series.isArray() && !series.isEmpty()) {
            for (JsonNode value : series.get(0).path("values")) {
                points.add(Map.of("time", value.path(0).asLong(), "value", value.path(1).asDouble(0)));
            }
        }
        return Map.of("metric", metric, "kind", kind, "instance", instance, "points", points);
    }

    /** 读取 Prometheus 中当前 firing/pending 的告警。 */
    public List<Map<String, String>> alerts() {
        JsonNode nodes = prometheus("/api/v1/alerts").path("data").path("alerts");
        List<Map<String, String>> alerts = new ArrayList<>();
        for (JsonNode alert : nodes) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", alert.path("labels").path("alertname").asText());
            item.put("severity", alert.path("labels").path("severity").asText());
            item.put("instance", alert.path("labels").path("instance").asText());
            item.put("service", alert.path("labels").path("service").asText());
            item.put("summary", alert.path("annotations").path("summary").asText());
            item.put("state", alert.path("state").asText());
            item.put("activeAt", alert.path("activeAt").asText());
            alerts.add(item);
        }
        return alerts;
    }

    private static String expression(String kind, String instance, String metric) {
        String selector = "{job=\"" + job(kind) + "\",instance=\"" + safeLabel(instance) + "\"}";
        return switch (kind + ":" + metric) {
            case "host:cpu" -> "100 * (1 - avg(rate(node_cpu_seconds_total{job=\"liteshop-host\",mode=\"idle\",instance=\"" + safeLabel(instance) + "\"}[5m])))";
            case "host:memory" -> "100 * (1 - node_memory_MemAvailable_bytes" + selector + " / node_memory_MemTotal_bytes" + selector + ")";
            case "host:disk" -> "100 * (1 - min(node_filesystem_avail_bytes{job=\"liteshop-host\",instance=\"" + safeLabel(instance) + "\",fstype!~\"tmpfs|overlay\"} / node_filesystem_size_bytes{job=\"liteshop-host\",instance=\"" + safeLabel(instance) + "\",fstype!~\"tmpfs|overlay\"}))";
            case "application:heap" -> "sum(jvm_memory_used_bytes{job=\"liteshop-app\",area=\"heap\",instance=\"" + safeLabel(instance) + "\"})";
            case "application:requests" -> "sum(rate(http_server_requests_seconds_count" + selector + "[5m]))";
            case "mysql:connections" -> "mysql_global_status_threads_connected" + selector;
            case "redis:memory" -> "redis_memory_used_bytes" + selector;
            case "rabbitmq:messages" -> "sum(rabbitmq_queue_messages" + selector + ")";
            default -> throw new IllegalArgumentException("不支持的指标");
        };
    }

    private static String safeLabel(String value) {
        if (!value.matches("[A-Za-z0-9.\\[\\]:_-]{1,255}")) {
            throw new IllegalArgumentException("无效的实例地址");
        }
        return value;
    }

    private static String job(String kind) {
        return kind.equals("application") ? "liteshop-app" :
                kind.equals("host") ? "liteshop-host" : "liteshop-" + kind;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode prometheus(String path) {
        if (baseUrl.isBlank()) throw new IllegalStateException("尚未配置 PROMETHEUS_BASE_URL");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Prometheus 查询失败");
            JsonNode body = objectMapper.readTree(response.body());
            if (!"success".equals(body.path("status").asText())) {
                throw new IllegalStateException("Prometheus 返回失败状态");
            }
            return body;
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接 Prometheus", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Prometheus 查询已中断", exception);
        }
    }
}
