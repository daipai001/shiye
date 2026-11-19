package com.shiye.user.module.article.domain.entity;

import lombok.Data;

@Data
public class ArticleFolderRow {
    private Long articleId;
    private Long folderId;
    private String folderPath;
}
