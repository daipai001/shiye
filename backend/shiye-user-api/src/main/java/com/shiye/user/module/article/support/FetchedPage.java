package com.shiye.user.module.article.support;

public record FetchedPage(
        byte[] body,
        String contentType,
        String finalUrl
) {
}
