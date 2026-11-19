package com.shiye.user.module.chat.service;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.common.settings.SystemSettings;
import com.shiye.user.common.ai.embedding.EmbeddingService;
import com.shiye.user.common.ai.rerank.RerankException;
import com.shiye.user.common.ai.rerank.RerankResult;
import com.shiye.user.common.ai.rerank.RerankService;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import com.shiye.user.module.article.domain.entity.ArticleChunkSearchRow;
import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.article.mapper.ArticleChunkMapper;
import com.shiye.user.module.article.mapper.ArticleFolderMapper;
import com.shiye.user.module.article.mapper.ArticleMapper;
import com.shiye.user.module.chat.support.RetrievalResult;
import com.shiye.user.module.chat.support.RetrievedChunk;
import com.shiye.user.module.tag.service.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RAG 检索：
 * - 向量召回：Redis 读取用户 chunk 向量，应用层余弦；
 * - 关键词召回：MySQL ngram FULLTEXT；
 * - RRF 融合两路排名；
 * - BGE-reranker 对融合候选池二次重排，失败时回退 RRF。
 * 部署 Redis Stack 后可将向量部分替换为 RediSearch HNSW。
 */
@Service
public class ChunkRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(ChunkRetrievalService.class);
    private static final int MAX_RERANK_DOC_CHARS = 4000;

    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;
    private final ArticleFolderMapper folderMapper;
    private final ArticleChunkMapper chunkMapper;
    private final RerankService rerankService;
    private final RuntimeSystemSettingsService systemSettingsService;
    private final TagService tagService;
    private final int maxPerArticle;
    private final double minScore;
    private final int maxScanChunks;
    private final int vectorCandidates;
    private final int keywordCandidates;
    private final int rrfK;
    private final int rerankCandidates;

    public ChunkRetrievalService(
            EmbeddingService embeddingService,
            StringRedisTemplate redisTemplate,
            ArticleMapper articleMapper,
            ArticleFolderMapper folderMapper,
            ArticleChunkMapper chunkMapper,
            RerankService rerankService,
            RuntimeSystemSettingsService systemSettingsService,
            TagService tagService,
            @Value("${shiye.ai.retrieval.max-per-article:3}") int maxPerArticle,
            @Value("${shiye.ai.retrieval.min-score:0.2}") double minScore,
            @Value("${shiye.ai.retrieval.max-scan-chunks:5000}") int maxScanChunks,
            @Value("${shiye.ai.retrieval.vector-candidates:30}") int vectorCandidates,
            @Value("${shiye.ai.retrieval.keyword-candidates:20}") int keywordCandidates,
            @Value("${shiye.ai.retrieval.rrf-k:60}") int rrfK,
            @Value("${shiye.ai.retrieval.rerank-candidates:20}") int rerankCandidates) {
        this.embeddingService = embeddingService;
        this.redisTemplate = redisTemplate;
        this.articleMapper = articleMapper;
        this.folderMapper = folderMapper;
        this.chunkMapper = chunkMapper;
        this.rerankService = rerankService;
        this.systemSettingsService = systemSettingsService;
        this.tagService = tagService;
        this.maxPerArticle = Math.max(1, maxPerArticle);
        this.minScore = minScore;
        this.maxScanChunks = Math.max(100, maxScanChunks);
        this.vectorCandidates = Math.max(1, Math.min(vectorCandidates, 100));
        this.keywordCandidates = Math.max(1, Math.min(keywordCandidates, 100));
        this.rrfK = Math.max(1, rrfK);
        this.rerankCandidates = Math.max(1, Math.min(rerankCandidates, 50));
    }

    public RetrievalResult retrieve(long userId, String query,
                                    String scopeType, Long scopeId, Integer requestedTopK) {
        String scope = scopeType == null || scopeType.isBlank()
                ? "ALL"
                : scopeType.trim().toUpperCase(Locale.ROOT);
        Set<Long> allowedArticleIds = resolveScope(userId, scope, scopeId);
        if (allowedArticleIds != null && allowedArticleIds.isEmpty()) {
            return RetrievalResult.withoutRerank(List.of());
        }
        SystemSettings settings = systemSettingsService.current();
        int defaultTopK = settings.ragTopK();
        boolean hybridEnabled = Boolean.TRUE.equals(settings.ragHybridEnabled());
        boolean rerankEnabled = Boolean.TRUE.equals(settings.ragRerankEnabled());

        List<Candidate> vectorResults = vectorCandidates(userId, query, allowedArticleIds);
        List<Candidate> keywordResults = hybridEnabled
                ? keywordCandidates(userId, query, scope, scopeId, allowedArticleIds)
                : List.of();
        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            return RetrievalResult.withoutRerank(List.of());
        }

        List<Candidate> pool = buildCandidatePool(vectorResults, keywordResults);
        RerankResult rerankResult = null;
        if (rerankEnabled && rerankService.isEnabled() && !pool.isEmpty()) {
            rerankResult = tryRerank(query, pool);
        }
        List<Candidate> selected = rerankResult == null
                ? fallbackSelect(vectorResults, keywordResults, requestedTopK, defaultTopK)
                : selectReranked(pool, rerankResult, requestedTopK, defaultTopK);
        if (selected.isEmpty()) {
            return RetrievalResult.withoutRerank(List.of());
        }

        List<Long> articleIds = selected.stream()
                .map(Candidate::articleId)
                .distinct()
                .toList();
        Map<Long, ArticleEntity> articles = new HashMap<>();
        for (ArticleEntity article : articleMapper.findByIdsAndUser(userId, articleIds)) {
            articles.put(article.getId(), article);
        }

        List<RetrievedChunk> results = new ArrayList<>(selected.size());
        for (Candidate candidate : selected) {
            ArticleEntity article = articles.get(candidate.articleId());
            if (article == null) {
                continue;
            }
            results.add(new RetrievedChunk(
                    candidate.chunkId(),
                    candidate.articleId(),
                    article.getTitle(),
                    article.getUrl(),
                    candidate.content(),
                    candidate.displayScore(),
                    candidate.charStart(),
                    candidate.charEnd()
            ));
        }
        if (rerankResult == null) {
            return RetrievalResult.withoutRerank(results);
        }
        return new RetrievalResult(results, true, rerankService.model(),
                rerankResult.inputTokens(), rerankResult.latencyMs());
    }

    private List<Candidate> vectorCandidates(long userId, String query, Set<Long> allowedArticleIds) {
        List<float[]> queryVectors = embeddingService.embed(List.of(query));
        if (queryVectors.isEmpty()) {
            return List.of();
        }
        float[] queryVector = queryVectors.get(0);
        String model = embeddingService.model();
        Set<String> chunkIds = redisTemplate.opsForSet().members(userKey(userId));
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        int scanned = 0;
        for (String chunkId : chunkIds) {
            if (++scanned > maxScanChunks) {
                break;
            }
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(chunkKey(chunkId));
            if (fields.isEmpty()) {
                continue;
            }
            if (!model.equals(stringValue(fields.get("embedding_model")))) {
                continue;
            }
            Long articleId = longValue(fields.get("article_id"));
            Long parsedChunkId = longValue(fields.get("chunk_id"));
            if (articleId == null || parsedChunkId == null) {
                continue;
            }
            if (allowedArticleIds != null && !allowedArticleIds.contains(articleId)) {
                continue;
            }
            float[] vector = decode(stringValue(fields.get("vector")));
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            Candidate candidate = new Candidate(
                    articleId,
                    parsedChunkId,
                    stringValue(fields.get("content")),
                    intValue(fields.get("char_start")),
                    intValue(fields.get("char_end"))
            );
            candidate.vectorScore = cosine(queryVector, vector);
            candidates.add(candidate);
        }

        double effectiveMinScore = model.startsWith("DEV-HASH") ? -1.0 : minScore;
        candidates.removeIf(candidate -> candidate.vectorScore() < effectiveMinScore);
        candidates.sort(Comparator.comparingDouble(Candidate::vectorScore).reversed());
        if (candidates.size() > vectorCandidates) {
            return new ArrayList<>(candidates.subList(0, vectorCandidates));
        }
        return candidates;
    }

    private List<Candidate> keywordCandidates(long userId, String query, String scope,
                                              Long scopeId, Set<Long> allowedArticleIds) {
        try {
            Long articleId = "ARTICLE".equals(scope) ? scopeId : null;
            List<Long> articleIds = allowedArticleIds != null
                    ? new ArrayList<>(allowedArticleIds)
                    : null;
            List<ArticleChunkSearchRow> rows = chunkMapper.searchKeyword(
                    userId, query, articleId, articleIds, keywordCandidates);
            List<Candidate> candidates = new ArrayList<>(rows.size());
            for (ArticleChunkSearchRow row : rows) {
                Candidate candidate = new Candidate(
                        row.getArticleId(),
                        row.getId(),
                        row.getContent(),
                        row.getCharStart() == null ? 0 : row.getCharStart(),
                        row.getCharEnd() == null ? 0 : row.getCharEnd()
                );
                candidate.keywordScore = row.getScore() == null ? 0 : row.getScore();
                candidates.add(candidate);
            }
            return candidates;
        } catch (RuntimeException ex) {
            log.warn("keyword retrieval unavailable, fallback to vector only: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 构建 rerank 候选池：两路都有时按 RRF 排，只有一路时按该路分数排；
     * 池子同样限制单篇数量，避免长文档挤占其他资料。
     */
    private List<Candidate> buildCandidatePool(List<Candidate> vectorResults,
                                               List<Candidate> keywordResults) {
        List<Candidate> ordered;
        if (!vectorResults.isEmpty() && !keywordResults.isEmpty()) {
            ordered = fuse(vectorResults, keywordResults);
        } else if (!vectorResults.isEmpty()) {
            ordered = new ArrayList<>(vectorResults);
            ordered.sort(Comparator.comparingDouble(Candidate::vectorScore).reversed());
        } else {
            ordered = new ArrayList<>(keywordResults);
            ordered.sort(Comparator.comparingDouble(Candidate::keywordScore).reversed());
        }
        return limitPerArticle(ordered, rerankCandidates);
    }

    private RerankResult tryRerank(String query, List<Candidate> pool) {
        List<String> documents = pool.stream()
                .map(candidate -> truncate(candidate.content(), MAX_RERANK_DOC_CHARS))
                .toList();
        try {
            RerankResult result = rerankService.rerank(query, documents);
            if (result.items().isEmpty()) {
                log.warn("rerank returned no items, fallback to hybrid order");
                return null;
            }
            return result;
        } catch (RerankException ex) {
            log.warn("rerank failed ({}), fallback to hybrid order: {}",
                    ex.getCode(), ex.getMessage());
            return null;
        } catch (RuntimeException ex) {
            log.warn("rerank failed, fallback to hybrid order: {}", ex.getMessage());
            return null;
        }
    }

    private List<Candidate> selectReranked(List<Candidate> pool, RerankResult rerankResult,
                                           Integer requestedTopK, int defaultTopK) {
        Map<Integer, Double> scoreByIndex = new HashMap<>();
        for (RerankResult.Item item : rerankResult.items()) {
            scoreByIndex.put(item.index(), item.score());
        }
        for (int i = 0; i < pool.size(); i++) {
            Candidate candidate = pool.get(i);
            Double score = scoreByIndex.get(i);
            if (score != null) {
                candidate.hasRerankScore = true;
                candidate.rerankScore = score;
                candidate.displayScoreValue = score;
            }
        }
        List<Candidate> ordered = new ArrayList<>(pool);
        ordered.sort(Comparator
                .comparingDouble(Candidate::rerankScore).reversed()
                .thenComparing(Comparator.comparingDouble(Candidate::displayScore).reversed()));
        return limitPerArticle(ordered, resolveTopK(requestedTopK, defaultTopK));
    }

    private List<Candidate> fallbackSelect(List<Candidate> vectorResults,
                                           List<Candidate> keywordResults,
                                           Integer requestedTopK, int defaultTopK) {
        if (keywordResults.isEmpty()) {
            return selectTop(vectorResults, requestedTopK, defaultTopK,
                    Comparator.comparingDouble(Candidate::vectorScore).reversed());
        }
        if (vectorResults.isEmpty()) {
            return selectTop(keywordResults, requestedTopK, defaultTopK,
                    Comparator.comparingDouble(Candidate::keywordScore).reversed());
        }
        return limitPerArticle(fuse(vectorResults, keywordResults),
                resolveTopK(requestedTopK, defaultTopK));
    }

    private List<Candidate> fuse(List<Candidate> vectorResults,
                                 List<Candidate> keywordResults) {
        Map<Long, Candidate> merged = new LinkedHashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            Candidate source = vectorResults.get(i);
            Candidate target = merged.computeIfAbsent(source.chunkId(), id -> source.copy());
            target.vectorScore = source.vectorScore;
            target.rrfScore += 1.0 / (rrfK + i + 1);
        }
        double maxKeyword = keywordResults.stream()
                .mapToDouble(Candidate::keywordScore)
                .max()
                .orElse(1.0);
        if (maxKeyword <= 0) {
            maxKeyword = 1.0;
        }
        for (int i = 0; i < keywordResults.size(); i++) {
            Candidate source = keywordResults.get(i);
            Candidate target = merged.computeIfAbsent(source.chunkId(), id -> source.copy());
            target.keywordScore = source.keywordScore;
            target.rrfScore += 1.0 / (rrfK + i + 1);
        }
        for (Candidate candidate : merged.values()) {
            double vectorPart = Math.max(0, candidate.vectorScore);
            double keywordPart = Math.min(1.0, candidate.keywordScore / maxKeyword) * 0.85;
            candidate.displayScoreValue = Math.max(vectorPart, keywordPart);
        }
        List<Candidate> ordered = new ArrayList<>(merged.values());
        ordered.sort(Comparator
                .comparingDouble(Candidate::rrfScore).reversed()
                .thenComparing(Comparator.comparingDouble(Candidate::displayScore).reversed()));
        return ordered;
    }

    private List<Candidate> selectTop(List<Candidate> candidates, Integer requestedTopK,
                                      int defaultTopK, Comparator<Candidate> comparator) {
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(comparator);
        return limitPerArticle(ordered, resolveTopK(requestedTopK, defaultTopK));
    }

    private int resolveTopK(Integer requestedTopK, int defaultTopK) {
        return requestedTopK == null ? defaultTopK : Math.max(1, Math.min(requestedTopK, 20));
    }

    private List<Candidate> limitPerArticle(List<Candidate> ordered, int topK) {
        Map<Long, Integer> perArticle = new HashMap<>();
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : ordered) {
            if (selected.size() >= topK) {
                break;
            }
            int count = perArticle.getOrDefault(candidate.articleId(), 0);
            if (count >= maxPerArticle) {
                continue;
            }
            perArticle.put(candidate.articleId(), count + 1);
            if (!candidate.hasRerankScore && candidate.displayScoreValue == 0) {
                candidate.displayScoreValue = candidate.vectorScore() > 0
                        ? candidate.vectorScore()
                        : Math.min(0.85, Math.max(0, candidate.keywordScore() / 10.0));
            }
            selected.add(candidate);
        }
        return selected;
    }

    private Set<Long> resolveScope(long userId, String scope, Long scopeId) {
        if ("ALL".equals(scope)) {
            return null;
        }
        if (scopeId == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "scope.id 不能为空");
        }
        if ("ARTICLE".equals(scope)) {
            return Set.of(scopeId);
        }
        if ("FOLDER".equals(scope)) {
            return new HashSet<>(folderMapper.findArticleIdsByFolder(userId, scopeId));
        }
        if ("TAG".equals(scope)) {
            return new HashSet<>(tagService.findArticleIdsByTag(userId, scopeId));
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "当前仅支持 ALL / ARTICLE / FOLDER / TAG 范围");
    }

    private String userKey(long userId) {
        return "kb:user:" + userId + ":chunks";
    }

    private String chunkKey(String chunkId) {
        return "kb:chunk:" + chunkId;
    }

    private float[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length % Float.BYTES != 0) {
                return null;
            }
            float[] vector = new float[bytes.length / Float.BYTES];
            for (int i = 0; i < vector.length; i++) {
                int offset = i * Float.BYTES;
                int bits = (bytes[offset] & 0xff)
                        | ((bytes[offset + 1] & 0xff) << 8)
                        | ((bytes[offset + 2] & 0xff) << 16)
                        | ((bytes[offset + 3] & 0xff) << 24);
                vector[i] = Float.intBitsToFloat(bits);
            }
            return vector;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA < 1e-12 || normB < 1e-12) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int intValue(Object value) {
        Long parsed = longValue(value);
        return parsed == null ? 0 : parsed.intValue();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class Candidate {
        private final long articleId;
        private final long chunkId;
        private final String content;
        private final int charStart;
        private final int charEnd;
        private double vectorScore;
        private double keywordScore;
        private double rrfScore;
        private double rerankScore = -1;
        private double displayScoreValue;
        private boolean hasRerankScore;

        private Candidate(long articleId, long chunkId, String content,
                          int charStart, int charEnd) {
            this.articleId = articleId;
            this.chunkId = chunkId;
            this.content = content;
            this.charStart = charStart;
            this.charEnd = charEnd;
        }

        private Candidate copy() {
            return new Candidate(articleId, chunkId, content, charStart, charEnd);
        }

        private long articleId() {
            return articleId;
        }

        private long chunkId() {
            return chunkId;
        }

        private String content() {
            return content;
        }

        private int charStart() {
            return charStart;
        }

        private int charEnd() {
            return charEnd;
        }

        private double vectorScore() {
            return vectorScore;
        }

        private double keywordScore() {
            return keywordScore;
        }

        private double rrfScore() {
            return rrfScore;
        }

        private double rerankScore() {
            return rerankScore;
        }

        private double displayScore() {
            return displayScoreValue;
        }
    }
}
