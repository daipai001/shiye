package com.shiye.user.module.article.domain.vo;

public record ArticleCollectResponse(
        String articleId,
        String status,
        boolean duplicate
) {
}
