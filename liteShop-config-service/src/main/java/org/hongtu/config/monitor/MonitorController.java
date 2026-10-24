package org.hongtu.config.monitor;

import cn.dev33.satoken.annotation.SaCheckRole;
import org.hongtu.Result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/** 平台监控接口及仅供 Prometheus 内网调用的目标发现接口。 */
@RestController
public class MonitorController {
    private final MonitorDiscoveryService discovery;
    private final MonitorMetricsService metrics;
    private final String discoveryToken;

    public MonitorController(MonitorDiscoveryService discovery, MonitorMetricsService metrics,
                             @Value("${monitor.discovery.token:}") String discoveryToken) {
        this.discovery = discovery;
        this.metrics = metrics;
        this.discoveryToken = discoveryToken;
    }

    /** Prometheus HTTP SD 格式；令牌未配置时关闭，避免匿名枚举内网地址。 */
    @GetMapping("/inner/config/monitor/discovery")
    public ResponseEntity<List<Map<String, Object>>> discovery(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam String kind) {
        if (discoveryToken.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        byte[] expected = ("Bearer " + discoveryToken).getBytes(StandardCharsets.UTF_8);
        byte[] provided = (authorization == null ? "" : authorization).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(discovery.discover(kind));
    }

    /** 获取服务器、服务和数据库的监控概览。 */
    @SaCheckRole("SUPER_ADMIN")
    @GetMapping("/config/monitor/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(metrics.overview());
    }

    /** 获取所有自动发现目标及抓取状态。 */
    @SaCheckRole("SUPER_ADMIN")
    @GetMapping("/config/monitor/targets")
    public Result<List<Map<String, Object>>> targets() {
        return Result.success(metrics.targets());
    }

    /** 按白名单指标和时间范围查询目标曲线。 */
    @SaCheckRole("SUPER_ADMIN")
    @GetMapping("/config/monitor/trend")
    public Result<Map<String, Object>> trend(@RequestParam String kind,
                                              @RequestParam String instance,
                                              @RequestParam String metric,
                                              @RequestParam(defaultValue = "60") int rangeMinutes) {
        return Result.success(metrics.trend(kind, instance, metric, rangeMinutes));
    }

    /** 获取当前触发或等待确认的告警。 */
    @SaCheckRole("SUPER_ADMIN")
    @GetMapping("/config/monitor/alerts")
    public Result<List<Map<String, String>>> alerts() {
        return Result.success(metrics.alerts());
    }
}
