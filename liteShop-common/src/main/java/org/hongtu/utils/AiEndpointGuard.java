package org.hongtu.utils;

import org.hongtu.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * 校验 AI 模型接口地址，拒绝明文 HTTP、本机、元数据和私网解析结果。
 */
@Component
public class AiEndpointGuard {

    /**
     * 可替换的地址解析，测试不访问外网。
     */
    @FunctionalInterface
    public interface AddressLookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
    }

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "localhost.localdomain",
            "metadata.google.internal",
            "metadata.internal"
    );

    private final AddressLookup lookup;

    public AiEndpointGuard() {
        this(InetAddress::getAllByName);
    }

    public AiEndpointGuard(AddressLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * 规范化模型基础地址。空字符串表示未配置，不触发解析。
     */
    public String normalize(String input) {
        String value = input == null ? "" : input.trim().replaceAll("/+$", "");
        if (value.isEmpty()) {
            return "";
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw invalid();
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || value.endsWith("/chat/completions") || blockedHost(host)) {
            throw invalid();
        }
        InetAddress[] addresses;
        try {
            addresses = lookup.lookup(host);
        } catch (UnknownHostException error) {
            throw invalid();
        }
        if (addresses == null || addresses.length == 0) {
            throw invalid();
        }
        for (InetAddress address : addresses) {
            if (address == null || isBlocked(address)) {
                throw invalid();
            }
        }
        // 官方 SDK 要求基础地址包含 API 前缀，裸域名补齐标准 /v1。
        return uri.getPath() == null || uri.getPath().isEmpty() ? value + "/v1" : value;
    }

    /**
     * 拒绝环回、链路本地、站点本地、ULA、组播、未指定地址和云元数据地址。
     */
    public static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 16 && (raw[0] & 0xfe) == 0xfc) {
            return true;
        }
        if (raw.length == 4) {
            int first = raw[0] & 0xff;
            int second = raw[1] & 0xff;
            int third = raw[2] & 0xff;
            int fourth = raw[3] & 0xff;
            // 阿里云元数据 100.100.100.200，不在站点本地网段内。
            return first == 100 && second == 100 && third == 100 && fourth == 200;
        }
        return false;
    }

    private static boolean blockedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return BLOCKED_HOSTS.contains(normalized) || normalized.endsWith(".localhost")
                || normalized.endsWith(".metadata.google.internal");
    }

    private static BusinessException invalid() {
        return new BusinessException("请填写公网 HTTPS 接口基础地址，例如 https://api.openai.com/v1，不包含 /chat/completions");
    }
}
