package com.shiye.user.module.article.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.common.security.Hashes;
import com.shiye.user.common.ai.embedding.EmbeddingException;
import com.shiye.user.common.ai.embedding.EmbeddingService;
import com.shiye.user.common.ai.vector.ChunkVectorStore;
import com.shiye.user.common.ai.vector.VectorStoreException;
import com.shiye.user.common.util.UrlNormalizer;
import com.shiye.user.module.article.domain.entity.ArticleChunkEntity;
import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.article.mapper.ArticleChunkMapper;
import com.shiye.user.module.article.mapper.ArticleFolderMapper;
import com.shiye.user.module.article.mapper.ArticleMapper;
import com.shiye.user.module.article.service.ArticleEnrichmentService;
import com.shiye.user.module.article.service.ArticleIngestService;
import com.shiye.user.module.article.support.ArticleStatus;
import com.shiye.user.module.article.support.FetchedPage;
import com.shiye.user.module.article.support.HtmlArticleParser;
import com.shiye.user.module.article.support.HttpPageFetcher;
import com.shiye.user.module.article.support.IngestException;
import com.shiye.user.module.article.support.ParsedArticle;
import com.shiye.user.module.article.support.TextChunk;
import com.shiye.user.module.article.support.TextChunker;
import com.shiye.user.module.article.support.TextStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class ArticleIngestServiceImpl implements ArticleIngestService {

    private static final Logger log = LoggerFactory.getLogger(ArticleIngestServiceImpl.class);

    private final ArticleMapper articleMapper;
    private final ArticleChunkMapper chunkMapper;
    private final ArticleFolderMapper folderMapper;
    private final HttpPageFetcher pageFetcher;
    private final HtmlArticleParser articleParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final ChunkVectorStore vectorStore;
    private final ArticleEnrichmentService enrichmentService;
    private final Executor ingestExecutor;
    private final int maxTextChars;

    public ArticleIngestServiceImpl(ArticleMapper articleMapper,
                                    ArticleChunkMapper chunkMapper,
                                    ArticleFolderMapper folderMapper,
                                    HttpPageFetcher pageFetcher,
                                    HtmlArticleParser articleParser,
                                    TextChunker textChunker,
                                    EmbeddingService embeddingService,
                                    ChunkVectorStore vectorStore,
                                    ArticleEnrichmentService enrichmentService,
                                    @Qualifier("ingestExecutor") Executor ingestExecutor,
                                    @Value("${shiye.article.max-text-chars:500000}") int maxTextChars) {
        this.articleMapper = articleMapper;
        this.chunkMapper = chunkMapper;
        this.folderMapper = folderMapper;
        this.pageFetcher = pageFetcher;
        this.articleParser = articleParser;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.enrichmentService = enrichmentService;
        this.ingestExecutor = ingestExecutor;
        this.maxTextChars = Math.max(10000, maxTextChars);
    }

    @Override
    public IngestOutcome ingestBookmark(long userId, long folderId, String folderPath,
                                        String url, String bookmarkTitle) {
        UrlNormalizer.Normalized normalized = normalize(url);
        return ingestResolved(userId, folderId, folderPath, normalized, bookmarkTitle,
                true, "BOOKMARK");
    }

    @Override
    public IngestOutcome collectUrl(long userId, String url, String title) {
        UrlNormalizer.Normalized normalized = normalize(url);
        return ingestResolved(userId, 0, null, normalized, title, true, "MANUAL");
    }

    @Override
    public IngestOutcome collectPage(long userId, String url, String title, String content) {
        if (content == null || content.isBlank()) {
            throw new IngestException("EMPTY_CONTENT", "当前页正文为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > maxTextChars) {
            trimmed = trimmed.substring(0, maxTextChars);
        }
        UrlNormalizer.Normalized normalized = normalize(url);
        String contentHash = Hashes.sha256Hex(trimmed);
        ArticleEntity article = articleMapper.findByUserAndUrlHash(userId, normalized.urlHash());
        if (article != null && ArticleStatus.READY.name().equals(article.getStatus())
                && contentHash.equals(article.getContentHash())) {
            return new IngestOutcome(article.getId(), true);
        }
        if (article == null) {
            article = createArticle(userId, normalized, title, "MANUAL");
        } else {
            articleMapper.updateStatus(article.getId(), userId, ArticleStatus.PENDING.name());
        }

        long articleId = article.getId();
        String finalTitle = title == null || title.isBlank()
                ? normalized.host() : title.trim();
        try {
            persistContent(
                    userId,
                    articleId,
                    finalTitle,
                    null,
                    TextStats.summary(trimmed, 220),
                    trimmed,
                    TextStats.language(trimmed),
                    TextStats.wordCount(trimmed)
            );
            return new IngestOutcome(articleId, false);
        } catch (IngestException ex) {
            markFailed(articleId, userId);
            throw ex;
        } catch (EmbeddingException ex) {
            markFailed(articleId, userId);
            throw new IngestException(ex.getCode(), ex.getMessage(), ex);
        } catch (VectorStoreException ex) {
            markFailed(articleId, userId);
            throw new IngestException("VECTOR_STORE_UNAVAILABLE", "向量库暂不可用", ex);
        } catch (Exception ex) {
            markFailed(articleId, userId);
            throw new IngestException("INGEST_FAILED", "入库失败", ex);
        }
    }

    @Override
    public void reindex(long userId, long articleId) {
        ArticleEntity article = articleMapper.findByIdAndUser(articleId, userId);
        if (article == null) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.getUrl() == null || article.getUrl().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "只有 URL 资料支持重新抓取");
        }
        UrlNormalizer.Normalized normalized = normalize(article.getUrl());
        articleMapper.updateStatus(articleId, userId, ArticleStatus.PENDING.name());
        String title = article.getTitle();
        String sourceType = article.getSourceType() == null ? "MANUAL" : article.getSourceType();
        ingestExecutor.execute(() -> {
            try {
                ingestResolved(userId, 0, null, normalized, title, false, sourceType);
            } catch (Exception ex) {
                log.warn("reindex article {} failed: {}", articleId, ex.getMessage());
            }
        });
    }

    private IngestOutcome ingestResolved(long userId, long folderId, String folderPath,
                                         UrlNormalizer.Normalized normalized,
                                         String bookmarkTitle, boolean skipIfReady,
                                         String sourceType) {
        ArticleEntity article = articleMapper.findByUserAndUrlHash(userId, normalized.urlHash());
        if (skipIfReady && article != null
                && ArticleStatus.READY.name().equals(article.getStatus())) {
            linkFolder(userId, article.getId(), folderId, folderPath);
            return new IngestOutcome(article.getId(), true);
        }
        if (article == null) {
            article = createArticle(userId, normalized, bookmarkTitle, sourceType);
        } else {
            articleMapper.updateStatus(article.getId(), userId, ArticleStatus.PENDING.name());
        }

        long articleId = article.getId();
        linkFolder(userId, articleId, folderId, folderPath);

        try {
            FetchedPage page = pageFetcher.fetch(normalized.normalizedUrl());
            articleMapper.updateStatus(articleId, userId, ArticleStatus.PARSING.name());

            ParsedArticle parsed = articleParser.parse(
                    page.body(), page.finalUrl(), bookmarkTitle);
            persistContent(
                    userId,
                    articleId,
                    parsed.title(),
                    parsed.author(),
                    parsed.summary(),
                    parsed.contentText(),
                    parsed.language(),
                    parsed.wordCount()
            );
            return new IngestOutcome(articleId, false);
        } catch (IngestException ex) {
            markFailed(articleId, userId);
            throw ex;
        } catch (EmbeddingException ex) {
            markFailed(articleId, userId);
            throw new IngestException(ex.getCode(), ex.getMessage(), ex);
        } catch (VectorStoreException ex) {
            markFailed(articleId, userId);
            throw new IngestException("VECTOR_STORE_UNAVAILABLE", "向量库暂不可用", ex);
        } catch (Exception ex) {
            markFailed(articleId, userId);
            throw new IngestException("INGEST_FAILED", "入库失败", ex);
        }
    }

    private void persistContent(long userId, long articleId, String title, String author,
                                String summary, String rawContent, String language,
                                int wordCount) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.length() > maxTextChars) {
            content = content.substring(0, maxTextChars);
        }
        if (content.length() < 80) {
            throw new IngestException("PARSE_EMPTY", "没有有效正文");
        }
        String contentHash = Hashes.sha256Hex(content);
        LocalDateTime fetchedAt = LocalDateTime.now(ZoneOffset.UTC);
        articleMapper.updateParsed(
                articleId,
                userId,
                truncate(title, 512),
                author,
                summary,
                content,
                contentHash,
                wordCount,
                language,
                fetchedAt,
                ArticleStatus.CHUNKING.name()
        );

        List<TextChunk> chunks = textChunker.chunk(content);
        if (chunks.isEmpty()) {
            throw new IngestException("CHUNK_EMPTY", "没有生成有效切片");
        }

        deleteOldChunks(userId, articleId);
        List<ArticleChunkEntity> stored = insertChunks(userId, articleId, chunks);
        articleMapper.updateStatus(articleId, userId, ArticleStatus.EMBEDDING.name());

        List<float[]> vectors = embeddingService.embed(
                stored.stream().map(ArticleChunkEntity::getContent).toList());
        if (vectors.size() != stored.size()) {
            throw new IngestException("EMBEDDING_BAD_RESPONSE", "Embedding 返回数量不匹配");
        }

        for (int i = 0; i < stored.size(); i++) {
            ArticleChunkEntity chunk = stored.get(i);
            String vectorId = "kb:chunk:" + chunk.getId();
            chunkMapper.updateVectorId(chunk.getId(), userId, vectorId);
            vectorStore.upsert(
                    userId,
                    articleId,
                    chunk.getId(),
                    embeddingService.model(),
                    chunk.getContent(),
                    chunk.getCharStart(),
                    chunk.getCharEnd(),
                    vectors.get(i)
            );
        }

        articleMapper.updateStatus(articleId, userId, ArticleStatus.READY.name());
        log.info("ingested article {} for user {}: {} chunks, model={}",
                articleId, userId, stored.size(), embeddingService.model());
        enrichmentService.enrichAsync(userId, articleId);
    }

    private UrlNormalizer.Normalized normalize(String url) {
        try {
            return UrlNormalizer.normalize(url);
        } catch (BizException ex) {
            throw new IngestException("INVALID_URL", ex.getMessage(), ex);
        }
    }

    private ArticleEntity createArticle(long userId, UrlNormalizer.Normalized normalized,
                                        String bookmarkTitle, String sourceType) {
        String title = bookmarkTitle == null || bookmarkTitle.isBlank()
                ? normalized.host()
                : bookmarkTitle.trim();
        ArticleEntity article = new ArticleEntity();
        article.setUserId(userId);
        article.setTitle(truncate(title, 512));
        article.setUrl(normalized.originalUrl());
        article.setUrlHash(normalized.urlHash());
        article.setDomain(normalized.host());
        article.setSourceType(sourceType == null ? "MANUAL" : sourceType);
        article.setStatus(ArticleStatus.PENDING.name());
        try {
            articleMapper.insert(article);
            return article;
        } catch (DuplicateKeyException ex) {
            ArticleEntity existing = articleMapper.findByUserAndUrlHash(
                    userId, normalized.urlHash());
            if (existing == null) {
                throw ex;
            }
            return existing;
        }
    }

    private List<ArticleChunkEntity> insertChunks(long userId, long articleId,
                                                  List<TextChunk> chunks) {
        List<ArticleChunkEntity> stored = new ArrayList<>(chunks.size());
        for (TextChunk chunk : chunks) {
            ArticleChunkEntity entity = new ArticleChunkEntity();
            entity.setArticleId(articleId);
            entity.setUserId(userId);
            entity.setChunkIndex(chunk.index());
            entity.setContent(chunk.content());
            entity.setTokenCount(chunk.tokenCount());
            entity.setCharStart(chunk.charStart());
            entity.setCharEnd(chunk.charEnd());
            entity.setEmbeddingModel(embeddingService.model());
            entity.setEmbeddingDim(embeddingService.dimension());
            entity.setVectorId("pending:" + UUID.randomUUID());
            entity.setContentHash(chunk.contentHash());
            chunkMapper.insert(entity);
            stored.add(entity);
        }
        return stored;
    }

    private void deleteOldChunks(long userId, long articleId) {
        List<ArticleChunkEntity> oldChunks = chunkMapper.findByArticle(articleId, userId);
        for (ArticleChunkEntity old : oldChunks) {
            vectorStore.delete(userId, old.getId());
        }
        chunkMapper.deleteByArticle(articleId, userId);
    }

    private void linkFolder(long userId, long articleId, long folderId, String folderPath) {
        if (folderId <= 0) {
            return;
        }
        folderMapper.insertIgnore(articleId, userId, folderId, folderPath);
    }

    private void markFailed(long articleId, long userId) {
        try {
            articleMapper.updateStatus(articleId, userId, ArticleStatus.FAILED.name());
        } catch (RuntimeException ex) {
            log.warn("mark article {} failed: {}", articleId, ex.getMessage());
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
