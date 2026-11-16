package com.shiye.user.module.article.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.ai.vector.ChunkVectorStore;
import com.shiye.user.module.article.domain.entity.ArticleChunkEntity;
import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.article.domain.vo.ArticleChunkVO;
import com.shiye.user.module.article.domain.vo.ArticleDetailVO;
import com.shiye.user.module.article.domain.vo.ArticleStatsVO;
import com.shiye.user.module.article.domain.vo.ArticleSummaryVO;
import com.shiye.user.module.article.mapper.ArticleChunkMapper;
import com.shiye.user.module.article.mapper.ArticleMapper;
import com.shiye.user.module.article.service.ArticleQueryService;
import com.shiye.user.module.article.support.ArticleSourceType;
import com.shiye.user.module.article.support.ArticleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ArticleQueryServiceImpl implements ArticleQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ArticleMapper articleMapper;
    private final ArticleChunkMapper chunkMapper;
    private final ChunkVectorStore vectorStore;

    public ArticleQueryServiceImpl(ArticleMapper articleMapper,
                                   ArticleChunkMapper chunkMapper,
                                   ChunkVectorStore vectorStore) {
        this.articleMapper = articleMapper;
        this.chunkMapper = chunkMapper;
        this.vectorStore = vectorStore;
    }

    @Override
    public PageResult<ArticleSummaryVO> list(long userId, int page, int pageSize,
                                             String q, String status, String sourceType) {
        int safePage = Math.max(1, page);
        int safeSize = safePageSize(pageSize);
        String qFilter = normalizeQuery(q);
        String statusFilter = status == null || status.isBlank()
                ? null
                : ArticleStatus.parse(status).name();
        String sourceFilter = sourceType == null || sourceType.isBlank()
                ? null
                : ArticleSourceType.parse(sourceType).name();

        long total = articleMapper.count(userId, qFilter, statusFilter, sourceFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<ArticleSummaryVO> items = articleMapper
                .findPage(userId, qFilter, statusFilter, sourceFilter,
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(ArticleSummaryVO::from)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public ArticleDetailVO detail(long userId, long articleId) {
        ArticleEntity article = findOwned(userId, articleId);
        article.setChunkCount((int) chunkMapper.countByArticle(articleId, userId));
        return ArticleDetailVO.from(article);
    }

    @Override
    public PageResult<ArticleChunkVO> chunks(long userId, long articleId, int page, int pageSize) {
        findOwned(userId, articleId);
        int safePage = Math.max(1, page);
        int safeSize = safePageSize(pageSize);
        long total = chunkMapper.countByArticle(articleId, userId);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<ArticleChunkVO> items = chunkMapper
                .findPage(articleId, userId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(ArticleChunkVO::from)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public ArticleStatsVO stats(long userId) {
        long total = articleMapper.count(userId, null, null, null);
        long ready = articleMapper.count(userId, null, ArticleStatus.READY.name(), null);
        long failed = articleMapper.count(userId, null, ArticleStatus.FAILED.name(), null);
        long processing = articleMapper.countProcessing(userId);
        return new ArticleStatsVO(total, ready, processing, failed);
    }

    @Override
    @Transactional
    public void delete(long userId, long articleId) {
        findOwned(userId, articleId);
        List<ArticleChunkEntity> chunks = chunkMapper.findByArticle(articleId, userId);
        for (ArticleChunkEntity chunk : chunks) {
            vectorStore.delete(userId, chunk.getId());
        }
        chunkMapper.deleteByArticle(articleId, userId);
        articleMapper.softDelete(articleId, userId);
    }

    private ArticleEntity findOwned(long userId, long articleId) {
        ArticleEntity article = articleMapper.findByIdAndUser(articleId, userId);
        if (article == null) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
    }
}
