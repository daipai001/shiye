package com.shiye.user.module.article.support;

public record ParsedArticle(
        String title,
        String author,
        String summary,
        String contentText,
        String language,
        int wordCount
) {
}
