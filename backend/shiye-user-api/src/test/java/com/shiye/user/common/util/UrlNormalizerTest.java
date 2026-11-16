package com.shiye.user.common.util;

import com.shiye.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlNormalizerTest {

    @Test
    void removesTrackingFragmentAndTrailingSlash() {
        UrlNormalizer.Normalized first = UrlNormalizer.normalize(
                "https://Example.com/rag/?utm_source=newsletter&b=2&a=1#section");
        UrlNormalizer.Normalized second = UrlNormalizer.normalize(
                "https://example.com/rag?a=1&b=2");

        assertEquals("https://example.com/rag?a=1&b=2", first.normalizedUrl());
        assertEquals(first.urlHash(), second.urlHash());
        assertEquals("https://Example.com/rag/?utm_source=newsletter&b=2&a=1#section",
                first.originalUrl());
    }

    @Test
    void keepsDistinctPathsAndQueriesDistinct() {
        UrlNormalizer.Normalized a = UrlNormalizer.normalize("https://example.com/a");
        UrlNormalizer.Normalized b = UrlNormalizer.normalize("https://example.com/b");
        UrlNormalizer.Normalized c = UrlNormalizer.normalize("https://example.com/a?page=2");

        assertNotEquals(a.urlHash(), b.urlHash());
        assertNotEquals(a.urlHash(), c.urlHash());
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(BizException.class,
                () -> UrlNormalizer.normalize("ftp://example.com/file"));
        assertThrows(BizException.class,
                () -> UrlNormalizer.normalize("javascript:alert(1)"));
        assertThrows(BizException.class,
                () -> UrlNormalizer.normalize("not-a-url"));
    }

    @Test
    void removesDefaultPorts() {
        UrlNormalizer.Normalized http = UrlNormalizer.normalize("http://example.com:80/path");
        UrlNormalizer.Normalized https = UrlNormalizer.normalize("https://example.com:443/path");

        assertEquals("http://example.com/path", http.normalizedUrl());
        assertEquals("https://example.com/path", https.normalizedUrl());
    }
}
