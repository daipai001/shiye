package com.shiye.user.module.article.service;

public interface ArticleIngestService {

    IngestOutcome ingestBookmark(long userId, long folderId, String folderPath,
                                 String url, String bookmarkTitle);

    IngestOutcome collectUrl(long userId, String url, String title);

    IngestOutcome collectPage(long userId, String url, String title, String content);

    UploadOutcome upload(long userId, String originalName, String mimeType,
                         byte[] bytes, String title);

    void reindex(long userId, long articleId);

    record IngestOutcome(long articleId, boolean duplicate) {
    }

    record UploadOutcome(long fileAssetId, long articleId, boolean duplicate) {
    }
}
