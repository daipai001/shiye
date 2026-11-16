package com.shiye.user.module.article.support;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 抓取前 SSRF 检查：协议、主机名、解析后的 IP 地址。
 *
 * <p>Java HttpClient 会再次解析域名，DNS rebinding 的残余风险在 V1 接受；
 * 生产环境可改为固定解析结果连接。</p>
 */
public final class UrlSafetyChecker {

    private UrlSafetyChecker() {
    }

    public static void assertSafe(URI uri) {
        if (uri == null) {
            throw new IngestException("INVALID_URL", "URL 为空");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IngestException("INVALID_URL", "只支持 http/https 链接");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IngestException("INVALID_URL", "URL 缺少域名");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || normalizedHost.endsWith(".internal")
                || normalizedHost.equals("metadata.google.internal")) {
            throw new IngestException("SSRF_BLOCKED", "不允许抓取内网地址");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new IngestException("DNS_FAILED", "域名解析失败", ex);
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new IngestException("SSRF_BLOCKED", "不允许抓取内网地址");
            }
        }
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0) {
                return true;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return true;
            }
            if (first == 169 && second == 254) {
                return true;
            }
        } else if (bytes.length == 16) {
            // IPv6 unique local fc00::/7
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
        }
        return false;
    }
}
