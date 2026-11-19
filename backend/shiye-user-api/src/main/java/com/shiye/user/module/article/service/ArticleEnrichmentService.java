package com.shiye.user.module.article.service;

public interface ArticleEnrichmentService {

    /**
     * 在资料 READY 后异步生成 AI 摘要和标签；失败不影响入库。
     */
    void enrichAsync(long userId, long articleId);
}
