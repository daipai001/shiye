package com.shiye.user.module.article.service;

import com.shiye.common.api.PageResult;
import com.shiye.user.module.article.domain.vo.ArticleChunkVO;
import com.shiye.user.module.article.domain.vo.ArticleDetailVO;
import com.shiye.user.module.article.domain.vo.ArticleStatsVO;
import com.shiye.user.module.article.domain.vo.ArticleSummaryVO;

public interface ArticleQueryService {

    PageResult<ArticleSummaryVO> list(long userId, int page, int pageSize,
                                      String q, String status, String sourceType, Long tagId);

    ArticleDetailVO detail(long userId, long articleId);

    PageResult<ArticleChunkVO> chunks(long userId, long articleId, int page, int pageSize);

    ArticleStatsVO stats(long userId);

    void delete(long userId, long articleId);

    void deleteByUrl(long userId, String url);
}
