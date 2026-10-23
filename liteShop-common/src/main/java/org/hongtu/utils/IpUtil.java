package org.hongtu.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * IP工具类
 * 提供IP获取、归属地查询等功能
 *
 * @author system
 * @since 2025-02-24
 */
@Slf4j
@Component
public class IpUtil {

    @Value("${ip.location.api.url:https://api.songzixian.com/api/ip}")
    private String ipLocationApiUrl;

    @Value("${ip.location.enabled:false}")
    private boolean ipLocationEnabled;

    /** 仅这些代理的转发头可参与客户端 IP 解析；默认不信任任何代理。 */
    @Value("${liteshop.security.trusted-proxies:}")
    private String trustedProxyCidrs;

    /**
     * 获取客户端真实IP地址
     * 支持代理、负载均衡、CDN等场景
     *
     * @param request HTTP请求对象
     * @return 客户端真实IP地址
     */
    public String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remote = normalize(request.getRemoteAddr());
        if (!isTrusted(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwarded) && forwarded.length() <= 2048) {
            String[] hops = forwarded.split(",", -1);
            String client = remote;
            // 从可信代理开始向左剥离；遇到第一个不可信地址即停止。
            for (int i = hops.length - 1; i >= 0 && isTrusted(client); i--) {
                String hop = normalize(hops[i].trim());
                if (!isValidIp(hop)) {
                    return remote;
                }
                client = hop;
            }
            return client;
        }
        String realIp = normalize(request.getHeader("X-Real-IP"));
        return isValidIp(realIp) ? realIp : remote;
    }

    /**
     * 验证IP是否有效
     *
     * @param ip IP地址
     * @return true-有效, false-无效
     */
    private static boolean isValidIp(String ip) {
        if (StrUtil.isBlank(ip)) return false;
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.", -1);
            if (parts.length != 4) return false;
            for (String part : parts) {
                if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) return false;
            }
            return true;
        }
        if (!ip.contains(":") || !ip.matches("[0-9a-fA-F:]+")) return false;
        try {
            return InetAddress.getByName(ip).getAddress().length == 16;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 从多IP字符串中获取第一个有效IP
     *
     * @param ips IP字符串(可能包含多个IP,用逗号分隔)
     * @return 第一个有效IP
     */
    private static String normalize(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        return isValidIp(ip) ? ip : "unknown";
    }

    private boolean isTrusted(String ip) {
        if (!isValidIp(ip) || StrUtil.isBlank(trustedProxyCidrs)) return false;
        for (String cidr : trustedProxyCidrs.split(",")) {
            String[] parts = cidr.trim().split("/", -1);
            if (parts.length > 2 || !isValidIp(parts[0])) continue;
            try {
                byte[] address = InetAddress.getByName(ip).getAddress();
                byte[] network = InetAddress.getByName(parts[0]).getAddress();
                if (address.length != network.length) continue;
                int bits = parts.length == 1 ? address.length * 8 : Integer.parseInt(parts[1]);
                if (bits < 0 || bits > address.length * 8) continue;
                boolean matches = true;
                for (int i = 0; i < address.length; i++) {
                    int mask = Math.max(0, Math.min(8, bits - i * 8));
                    int byteMask = (0xff << (8 - mask)) & 0xff;
                    if ((address[i] & byteMask) != (network[i] & byteMask)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) return true;
            } catch (Exception ignored) {
                // 无效配置不扩大可信范围。
            }
        }
        return false;
    }

    /**
     * 查询IP归属地信息
     * 支持对接第三方API,如: https://api.songzixian.com/api/ip
     *
     * @param ip IP地址
     * @return IP归属地信息对象
     */
    public IpLocationInfo getIpLocation(String ip) {
        if (!ipLocationEnabled) {
            log.debug("IP归属地查询功能未启用");
            return IpLocationInfo.unknown(ip);
        }

        if (StrUtil.isBlank(ip) || "unknown".equals(ip) || "127.0.0.1".equals(ip)) {
            return IpLocationInfo.local(ip);
        }

        try {
            String url = ipLocationApiUrl + "?ip=" + ip;
            String response = HttpUtil.get(url, 3000);
            
            if (StrUtil.isBlank(response)) {
                log.warn("IP归属地查询失败: 响应为空, IP: {}", ip);
                return IpLocationInfo.unknown(ip);
            }

            JSONObject jsonObject = JSON.parseObject(response);
            
            // 根据API返回格式解析(可根据实际API调整)
            if (jsonObject.getInteger("code") == 200) {
                JSONObject data = jsonObject.getJSONObject("data");
                return IpLocationInfo.builder()
                    .ip(ip)
                    .country(data.getString("country"))
                    .province(data.getString("province"))
                    .city(data.getString("city"))
                    .isp(data.getString("isp"))
                    .build();
            } else {
                log.warn("IP归属地查询失败: {}, IP: {}", jsonObject.getString("msg"), ip);
                return IpLocationInfo.unknown(ip);
            }
        } catch (Exception e) {
            log.error("IP归属地查询异常, IP: {}", ip, e);
            return IpLocationInfo.unknown(ip);
        }
    }

    /**
     * IP归属地信息对象
     */
    public static class IpLocationInfo {
        private String ip;
        private String country;
        private String province;
        private String city;
        private String isp;
        private boolean valid;

        public static IpLocationInfo unknown(String ip) {
            IpLocationInfo info = new IpLocationInfo();
            info.ip = ip;
            info.country = "未知";
            info.province = "未知";
            info.city = "未知";
            info.isp = "未知";
            info.valid = false;
            return info;
        }

        public static IpLocationInfo local(String ip) {
            IpLocationInfo info = new IpLocationInfo();
            info.ip = ip;
            info.country = "本地";
            info.province = "本地";
            info.city = "本地";
            info.isp = "本地";
            info.valid = true;
            return info;
        }

        public static IpLocationInfoBuilder builder() {
            return new IpLocationInfoBuilder();
        }

        public String getIp() {
            return ip;
        }

        public String getCountry() {
            return country;
        }

        public String getProvince() {
            return province;
        }

        public String getCity() {
            return city;
        }

        public String getIsp() {
            return isp;
        }

        public boolean isValid() {
            return valid;
        }

        public String getFullLocation() {
            if (!valid) {
                return "未知";
            }
            StringBuilder sb = new StringBuilder();
            if (StrUtil.isNotBlank(country)) {
                sb.append(country);
            }
            if (StrUtil.isNotBlank(province) && !"本地".equals(province)) {
                sb.append(province);
            }
            if (StrUtil.isNotBlank(city) && !"本地".equals(city)) {
                sb.append(city);
            }
            return sb.toString();
        }

        public static class IpLocationInfoBuilder {
            private final IpLocationInfo info = new IpLocationInfo();

            public IpLocationInfoBuilder ip(String ip) {
                info.ip = ip;
                return this;
            }

            public IpLocationInfoBuilder country(String country) {
                info.country = country;
                return this;
            }

            public IpLocationInfoBuilder province(String province) {
                info.province = province;
                return this;
            }

            public IpLocationInfoBuilder city(String city) {
                info.city = city;
                return this;
            }

            public IpLocationInfoBuilder isp(String isp) {
                info.isp = isp;
                return this;
            }

            public IpLocationInfo build() {
                info.valid = StrUtil.isNotBlank(info.country) 
                    && StrUtil.isNotBlank(info.province);
                return info;
            }
        }
    }
}
