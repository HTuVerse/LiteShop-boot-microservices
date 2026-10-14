package org.hongtu.ai.client;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/** 为模型厂商请求解析显式配置或标准代理环境变量，不影响内部 Feign 调用。 */
@Component
public class AiProxyResolver {
    private final Environment environment;

    public AiProxyResolver(Environment environment) {
        this.environment = environment;
    }

    /** 返回当前模型地址应使用的代理；未配置或命中 NO_PROXY 时返回 null。 */
    public Proxy resolve(String baseUrl) {
        URI target = parseUri(baseUrl, "模型接口地址");
        if (matchesNoProxy(target)) return null;
        String proxyUrl = firstNonBlank(
                environment.getProperty("lite-shop.ai.proxy-url"),
                isHttps(target) ? environment.getProperty("HTTPS_PROXY") : environment.getProperty("HTTP_PROXY"),
                isHttps(target) ? environment.getProperty("https_proxy") : environment.getProperty("http_proxy"),
                environment.getProperty("ALL_PROXY"),
                environment.getProperty("all_proxy"),
                isHttps(target) ? environment.getProperty("HTTP_PROXY") : null,
                isHttps(target) ? environment.getProperty("http_proxy") : null
        );
        if (!StringUtils.hasText(proxyUrl)) return null;
        return parseProxy(proxyUrl);
    }

    private Proxy parseProxy(String raw) {
        String value = raw.trim();
        if (!value.contains("://")) value = "http://" + value;
        URI uri = parseUri(value, "AI 模型代理地址");
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException("AI 模型代理暂不支持在 URL 中携带账号密码");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        Proxy.Type type = switch (scheme) {
            case "http", "https" -> Proxy.Type.HTTP;
            case "socks", "socks5", "socks5h" -> Proxy.Type.SOCKS;
            default -> throw new IllegalStateException("AI 模型代理仅支持 HTTP(S) 或 SOCKS5");
        };
        int port = uri.getPort();
        if (port < 0) port = type == Proxy.Type.SOCKS ? 1080 : "https".equals(scheme) ? 443 : 80;
        return new Proxy(type, InetSocketAddress.createUnresolved(uri.getHost(), port));
    }

    private boolean matchesNoProxy(URI target) {
        String configured = firstNonBlank(
                environment.getProperty("lite-shop.ai.no-proxy"),
                environment.getProperty("NO_PROXY"),
                environment.getProperty("no_proxy")
        );
        if (!StringUtils.hasText(configured)) return false;
        String host = target.getHost().toLowerCase(Locale.ROOT);
        int targetPort = target.getPort() >= 0 ? target.getPort() : isHttps(target) ? 443 : 80;
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(rule -> matchesRule(host, targetPort, rule.toLowerCase(Locale.ROOT)));
    }

    private boolean matchesRule(String host, int targetPort, String rule) {
        if ("*".equals(rule)) return true;
        String ruleHost = rule;
        Integer rulePort = null;
        int colon = rule.lastIndexOf(':');
        if (colon > 0 && rule.indexOf(':') == colon) {
            try {
                rulePort = Integer.parseInt(rule.substring(colon + 1));
                ruleHost = rule.substring(0, colon);
            } catch (NumberFormatException ignored) {
                // 冒号属于主机规则本身时按完整字符串匹配。
            }
        }
        if (rulePort != null && rulePort != targetPort) return false;
        if (ruleHost.startsWith("*.")) ruleHost = ruleHost.substring(1);
        if (ruleHost.startsWith(".")) return host.endsWith(ruleHost) || host.equals(ruleHost.substring(1));
        return host.equals(ruleHost);
    }

    private URI parseUri(String value, String label) {
        try {
            URI uri = URI.create(value);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(label + "格式不正确", error);
        }
    }

    private boolean isHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(StringUtils::hasText).findFirst().orElse("");
    }
}
