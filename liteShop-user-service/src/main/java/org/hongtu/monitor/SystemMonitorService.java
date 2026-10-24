package org.hongtu.monitor;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 分开维护管理端和小程序的在线状态与近七日访问统计。 */
@Service
@RequiredArgsConstructor
public class SystemMonitorService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);
    private static final Duration DETAIL_TTL = Duration.ofMinutes(6);
    private static final Duration HISTORY_TTL = Duration.ofDays(9);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private final StringRedisTemplate redis;
    private final SysUserMapper users;

    /** 只有正式登录后的对应端别账号才能上报，租户归属取自服务端用户记录。 */
    public void pulse(String client, boolean visit, String ip) {
        checkClient(client);
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = TenantIgnore.get(() -> users.selectById(userId));
        if (user == null) return;
        long tenantId = user.getTenantId() == null ? 0L : user.getTenantId();
        String digest = tokenDigest();
        String detailKey = "system-monitor:session:" + digest;
        long now = System.currentTimeMillis();
        var hash = redis.opsForHash();
        Map<String, String> details = new HashMap<>();
        details.put("client", client);
        details.put("userId", Long.toString(userId));
        details.put("tenantId", Long.toString(tenantId));
        details.put("account", Objects.toString(user.getUsername(), ""));
        details.put("nickname", Objects.toString(user.getNickname(), ""));
        details.put("ip", Objects.toString(ip, ""));
        details.put("lastSeen", Long.toString(now));
        hash.putAll(detailKey, details);
        hash.putIfAbsent(detailKey, "firstSeen", Long.toString(now));
        redis.expire(detailKey, DETAIL_TTL);
        touchIndex(indexKey(client, "all"), digest, now);
        touchIndex(indexKey(client, Long.toString(tenantId)), digest, now);

        // 同一令牌五秒内最多记一次访问，避免重复导航造成明显虚高。
        if (visit && Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                "system-monitor:visit-limit:" + digest, "1", Duration.ofSeconds(5)))) {
            LocalDateTime at = LocalDateTime.now(ZONE);
            recordVisit(client, "all", userId, at);
            recordVisit(client, Long.toString(tenantId), userId, at);
        }
    }

    /** 退出登录时立即移除当前令牌对应的在线状态。 */
    public void removeCurrent() {
        String digest = tokenDigest();
        String detailKey = "system-monitor:session:" + digest;
        Map<Object, Object> details = redis.opsForHash().entries(detailKey);
        if (details.isEmpty()) return;
        String client = Objects.toString(details.get("client"), "");
        String tenant = Objects.toString(details.get("tenantId"), "");
        if ("B".equals(client) || "C".equals(client)) {
            redis.opsForZSet().remove(indexKey(client, "all"), digest);
            redis.opsForZSet().remove(indexKey(client, tenant), digest);
        }
        redis.delete(detailKey);
    }

    /** 查询在线会话，超级管理员可以选租户，普通租户管理员始终限定本租户。 */
    public Map<String, Object> online(String client, Long requestedTenant, String keyword, String ip,
                                      int page, int size) {
        checkRead(client);
        String scope = queryScope(requestedTenant);
        long cutoff = System.currentTimeMillis() - ONLINE_WINDOW.toMillis();
        Set<String> ids = redis.opsForZSet().rangeByScore(indexKey(client, scope), cutoff, Double.POSITIVE_INFINITY);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ids != null) for (String id : ids) {
            Map<Object, Object> raw = redis.opsForHash().entries("system-monitor:session:" + id);
            if (raw.isEmpty() || !client.equals(raw.get("client"))) continue;
            long tenantId = parseLong(raw.get("tenantId"));
            if (!"all".equals(scope) && !scope.equals(Long.toString(tenantId))) continue;
            String account = Objects.toString(raw.get("account"), "");
            String nickname = Objects.toString(raw.get("nickname"), "");
            String clientIp = Objects.toString(raw.get("ip"), "");
            if (keyword != null && !keyword.isBlank()
                    && !account.contains(keyword.trim()) && !nickname.contains(keyword.trim())) continue;
            if (ip != null && !ip.isBlank() && !clientIp.contains(ip.trim())) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("id", id);
            row.put("userId", parseLong(raw.get("userId")));
            row.put("tenantId", tenantId == 0 ? null : tenantId);
            row.put("account", account);
            row.put("nickname", nickname);
            row.put("ip", clientIp);
            row.put("firstSeen", parseLong(raw.get("firstSeen")));
            row.put("lastSeen", parseLong(raw.get("lastSeen")));
            rows.add(row);
        }
        rows.sort(Comparator.comparingLong((Map<String, Object> row) -> (Long) row.get("lastSeen")).reversed());
        int from = Math.min(rows.size(), (page - 1) * size);
        int to = Math.min(rows.size(), from + size);
        return Map.of("records", rows.subList(from, to), "total", rows.size(),
                "current", page, "size", size);
    }

    /** 统计卡片、逐日趋势和二十四小时分布使用同一租户范围。 */
    public Map<String, Object> overview(String client, Long requestedTenant) {
        checkRead(client);
        String scope = queryScope(requestedTenant);
        LocalDate today = LocalDate.now(ZONE);
        List<Map<String, Object>> trend = new ArrayList<>();
        long sevenDayVisits = 0;
        for (int i = 6; i >= 0; i--) {
            String date = DAY.format(today.minusDays(i));
            long visits = value(counterKey(client, scope, date, "visits"));
            long visitors = size(visitorKey(client, scope, date));
            trend.add(Map.of("date", date, "visits", visits, "visitors", visitors));
            sevenDayVisits += visits;
        }
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            long visits = 0;
            for (int i = 0; i < 7; i++) {
                String date = DAY.format(today.minusDays(i));
                visits += value(counterKey(client, scope, date, String.format("hour:%02d", hour)));
            }
            hours.add(Map.of("hour", hour, "visits", visits));
        }
        Set<String> activeUsers = new HashSet<>();
        long cutoff = System.currentTimeMillis() - ONLINE_WINDOW.toMillis();
        Set<String> ids = redis.opsForZSet().rangeByScore(indexKey(client, scope), cutoff, Double.POSITIVE_INFINITY);
        int sessions = 0;
        if (ids != null) for (String id : ids) {
            Map<Object, Object> detail = redis.opsForHash().entries("system-monitor:session:" + id);
            if (client.equals(detail.get("client")) && ("all".equals(scope)
                    || scope.equals(Objects.toString(detail.get("tenantId"), "")))) {
                sessions++;
                activeUsers.add(Objects.toString(detail.get("userId"), ""));
            }
        }
        return Map.of("onlineUsers", activeUsers.size(), "onlineSessions", sessions,
                "todayVisits", trend.getLast().get("visits"), "sevenDayVisits", sevenDayVisits,
                "trend", trend, "hours", hours);
    }

    private void checkClient(String client) {
        if (!"B".equals(client) && !"C".equals(client)) throw new IllegalArgumentException("端别不合法");
        List<String> roles = StpUtil.getRoleList();
        if ("B".equals(client) && roles.stream().noneMatch(role -> !"USER".equals(role)))
            throw new IllegalArgumentException("当前账号不是管理端账号");
        if ("C".equals(client) && (roles.isEmpty() || roles.stream().anyMatch(role -> !"USER".equals(role))))
            throw new IllegalArgumentException("当前账号不是小程序用户");
    }

    private void checkRead(String client) {
        if (!"B".equals(client) && !"C".equals(client)) throw new IllegalArgumentException("端别不合法");
        if (!StpUtil.hasRole("SUPER_ADMIN")) StpUtil.checkPermission("system:monitor:" + client.toLowerCase());
    }

    private String queryScope(Long requestedTenant) {
        if (StpUtil.hasRole("SUPER_ADMIN")) {
            if (requestedTenant != null && requestedTenant <= 0) throw new IllegalArgumentException("租户ID不合法");
            return requestedTenant == null ? "all" : requestedTenant.toString();
        }
        Long tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId == null) throw new IllegalArgumentException("缺少当前租户");
        return tenantId.toString();
    }

    private void recordVisit(String client, String scope, long userId, LocalDateTime at) {
        String date = DAY.format(at);
        String counter = counterKey(client, scope, date, "visits");
        redis.opsForValue().increment(counter);
        redis.expire(counter, HISTORY_TTL);
        String hourly = counterKey(client, scope, date, String.format("hour:%02d", at.getHour()));
        redis.opsForValue().increment(hourly);
        redis.expire(hourly, HISTORY_TTL);
        String visitors = visitorKey(client, scope, date);
        redis.opsForSet().add(visitors, Long.toString(userId));
        redis.expire(visitors, HISTORY_TTL);
    }

    private void touchIndex(String key, String digest, long now) {
        redis.opsForZSet().add(key, digest, now);
        redis.opsForZSet().removeRangeByScore(key, 0, now - ONLINE_WINDOW.toMillis());
        redis.expire(key, Duration.ofDays(1));
    }

    private String indexKey(String client, String scope) {
        return "system-monitor:online:" + client + ":" + scope;
    }

    private String counterKey(String client, String scope, String date, String metric) {
        return "system-monitor:stats:" + client + ":" + scope + ":" + date + ":" + metric;
    }

    private String visitorKey(String client, String scope, String date) {
        return counterKey(client, scope, date, "users");
    }

    private long value(String key) {
        String raw = redis.opsForValue().get(key);
        return raw == null ? 0 : Long.parseLong(raw);
    }

    private long size(String key) {
        Long count = redis.opsForSet().size(key);
        return count == null ? 0 : count;
    }

    private long parseLong(Object value) {
        if (value == null) return 0;
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String tokenDigest() {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    StpUtil.getTokenValue().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("服务器缺少 SHA-256", error);
        }
    }
}
