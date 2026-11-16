package com.shiye.user.module.article.domain.vo;

public record ArticleStatsVO(
        long total,
        long ready,
        long processing,
        long failed
) {
}
