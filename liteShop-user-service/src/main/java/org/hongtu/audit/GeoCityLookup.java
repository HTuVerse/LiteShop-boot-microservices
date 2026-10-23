package org.hongtu.audit;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** 使用部署目录中的 GeoLite2 City 数据库离线解析审计 IP。 */
@Slf4j
@Component
public class GeoCityLookup {
    @Value("${liteshop.audit.geo-db-path:}")
    private String databasePath;
    @Value("${liteshop.audit.geo-db-version:unknown}")
    private String databaseVersion;
    private DatabaseReader reader;

    @PostConstruct
    public void open() {
        if (databasePath == null || databasePath.isBlank()) {
            log.warn("审计 IP 归属地数据库路径未配置，公网 IP 将记录为未知");
            return;
        }
        try {
            reader = new DatabaseReader.Builder(new File(databasePath)).build();
        } catch (Exception error) {
            throw new IllegalStateException("无法加载审计 IP 归属地数据库: " + databasePath, error);
        }
    }

    public Location lookup(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equals(ip)) return new Location("未知", databaseVersion);
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()) return new Location("本地/内网", databaseVersion);
            if (reader == null) return new Location("未知", databaseVersion);
            CityResponse city = reader.city(address);
            List<String> parts = new ArrayList<>();
            add(parts, city.getCountry().getNames().getOrDefault("zh-CN", city.getCountry().getName()));
            add(parts, city.getMostSpecificSubdivision().getNames().getOrDefault("zh-CN",
                    city.getMostSpecificSubdivision().getName()));
            add(parts, city.getCity().getNames().getOrDefault("zh-CN", city.getCity().getName()));
            return new Location(parts.isEmpty() ? "未知" : String.join(" ", parts), databaseVersion);
        } catch (Exception ignored) {
            return new Location("未知", databaseVersion);
        }
    }

    private void add(List<String> parts, String value) {
        if (value != null && !value.isBlank() && !parts.contains(value)) parts.add(value);
    }

    @PreDestroy
    public void close() throws Exception {
        if (reader != null) reader.close();
    }

    public record Location(String name, String databaseVersion) { }
}
