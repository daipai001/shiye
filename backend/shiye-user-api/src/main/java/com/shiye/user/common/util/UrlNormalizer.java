package com.shiye.user.common.util;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.common.security.Hashes;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * URL 规范化：去锚点、去 utm 等追踪参数、统一域名/端口/尾斜杠，再计算去重 hash。
 */
public final class UrlNormalizer {

    private static final int MAX_URL_LENGTH = 2048;

    private UrlNormalizer() {
    }

    public static Normalized normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.INVALID_URL, "URL 不能为空");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new BizException(ErrorCode.INVALID_URL, "URL 超过 2048 字符");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new BizException(ErrorCode.INVALID_URL, "URL 格式非法");
        }

        String scheme = lower(uri.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BizException(ErrorCode.INVALID_URL, "只支持 http/https 链接");
        }
        if (uri.getUserInfo() != null) {
            throw new BizException(ErrorCode.INVALID_URL, "URL 不能包含账号密码");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BizException(ErrorCode.INVALID_URL, "URL 缺少域名");
        }
        try {
            host = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.INVALID_URL, "URL 域名非法");
        }

        int port = uri.getPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        } else if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String query = normalizeQuery(uri.getRawQuery());

        StringBuilder builder = new StringBuilder();
        builder.append(scheme).append("://").append(host);
        if (port != -1) {
            builder.append(':').append(port);
        }
        builder.append(path);
        if (!query.isEmpty()) {
            builder.append('?').append(query);
        }

        String normalized = builder.toString();
        if (normalized.length() > MAX_URL_LENGTH) {
            throw new BizException(ErrorCode.INVALID_URL, "规范化后的 URL 超过 2048 字符");
        }
        return new Normalized(trimmed, normalized, Hashes.sha256Hex(normalized), host);
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            String rawKey = eq >= 0 ? part.substring(0, eq) : part;
            String key;
            try {
                key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException ex) {
                key = rawKey.toLowerCase(Locale.ROOT);
            }
            if (isTrackingParam(key)) {
                continue;
            }
            kept.add(part);
        }
        Collections.sort(kept);
        return String.join("&", kept);
    }

    private static boolean isTrackingParam(String key) {
        return key.startsWith("utm_")
                || "fbclid".equals(key)
                || "gclid".equals(key)
                || "yclid".equals(key)
                || "mc_cid".equals(key)
                || "mc_eid".equals(key);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record Normalized(String originalUrl, String normalizedUrl, String urlHash, String host) {
    }
}
