package com.shiye.user.module.article.domain.vo;

import com.shiye.user.module.article.domain.entity.ArticleFolderRow;

public record ArticleFolderVO(String id, String path, long articleCount) {

    public ArticleFolderVO(String id, String path) {
        this(id, path, 0);
    }

    public static ArticleFolderVO fromRow(ArticleFolderRow row) {
        return new ArticleFolderVO(String.valueOf(row.getFolderId()), row.getFolderPath());
    }
}
