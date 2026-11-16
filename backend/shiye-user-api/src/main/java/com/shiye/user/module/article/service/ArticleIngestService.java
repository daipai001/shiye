package com.shiye.user.module.article.service;

public interface ArticleIngestService {

    IngestOutcome ingestBookmark(long userId, long folderId, String folderPath,
                                 String url, String bookmarkTitle);

    IngestOutcome collectUrl(long userId, String url, String title);

    IngestOutcome collectPage(long userId, String url, String title, String content);

    void reindex(long userId, long articleId);

    record IngestOutcome(long articleId, boolean duplicate) {
    }
}
