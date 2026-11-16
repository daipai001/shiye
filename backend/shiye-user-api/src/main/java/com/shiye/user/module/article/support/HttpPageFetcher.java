package com.shiye.user.module.article.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;

@Component
public class HttpPageFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ShiYeBot/0.1; +https://localhost)";

    private final HttpClient httpClient;
    private final int timeoutSeconds;
    private final int maxPageBytes;

    public HttpPageFetcher(
            @Value("${shiye.bookmark.fetch-timeout-seconds:20}") int timeoutSeconds,
            @Value("${shiye.bookmark.max-page-bytes:5242880}") int maxPageBytes) {
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.maxPageBytes = Math.max(1024, maxPageBytes);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public FetchedPage fetch(String url) {
        URI current;
        try {
            current = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IngestException("INVALID_URL", "URL 格式非法", ex);
        }

        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            UrlSafetyChecker.assertSafe(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpTimeoutException ex) {
                throw new IngestException("FETCH_TIMEOUT", "抓取超时", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IngestException("FETCH_INTERRUPTED", "抓取被中断", ex);
            } catch (IOException ex) {
                throw new IngestException("FETCH_FAILED", "页面抓取失败", ex);
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null || location.isBlank()) {
                    throw new IngestException("FETCH_FAILED", "重定向缺少 Location");
                }
                current = current.resolve(location);
                continue;
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new IngestException("HTTP_" + status, "页面返回 HTTP " + status);
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!contentType.isBlank()
                    && !contentType.contains("text/html")
                    && !contentType.contains("application/xhtml+xml")) {
                closeQuietly(response.body());
                throw new IngestException("UNSUPPORTED_CONTENT_TYPE",
                        "暂不支持该内容类型：" + contentType);
            }

            long declaredLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1);
            if (declaredLength > maxPageBytes) {
                closeQuietly(response.body());
                throw new IngestException("PAGE_TOO_LARGE", "页面超过大小限制");
            }

            byte[] body = readBounded(response.body());
            return new FetchedPage(body, contentType, current.toString());
        }

        throw new IngestException("TOO_MANY_REDIRECTS", "重定向次数过多");
    }

    private byte[] readBounded(InputStream input) {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxPageBytes) {
                    throw new IngestException("PAGE_TOO_LARGE", "页面超过大小限制");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IngestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IngestException("FETCH_FAILED", "读取页面失败", ex);
        }
    }

    private void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // 忽略关闭失败
        }
    }
}
