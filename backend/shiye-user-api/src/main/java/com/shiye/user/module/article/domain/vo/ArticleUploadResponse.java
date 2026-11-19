package com.shiye.user.module.article.domain.vo;

public record ArticleUploadResponse(
        String fileAssetId,
        String articleId,
        String status,
        boolean duplicate
) {
}
