package com.shiye.user.module.chat.service;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.ai.embedding.EmbeddingService;
import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.article.mapper.ArticleFolderMapper;
import com.shiye.user.module.article.mapper.ArticleMapper;
import com.shiye.user.module.chat.support.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地 V1 检索：Redis 读取用户 chunk 向量，应用层余弦相似度。
 * 部署 Redis Stack 后替换为 RediSearch HNSW + user_id 过滤。
 */
@Service
public class ChunkRetrievalService {

    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;
    private final ArticleFolderMapper folderMapper;
    private final int defaultTopK;
    private final int maxPerArticle;
    private final double minScore;
    private final int maxScanChunks;

    public ChunkRetrievalService(
            EmbeddingService embeddingService,
            StringRedisTemplate redisTemplate,
            ArticleMapper articleMapper,
            ArticleFolderMapper folderMapper,
            @Value("${shiye.ai.retrieval.top-k:8}") int defaultTopK,
            @Value("${shiye.ai.retrieval.max-per-article:3}") int maxPerArticle,
            @Value("${shiye.ai.retrieval.min-score:0.2}") double minScore,
            @Value("${shiye.ai.retrieval.max-scan-chunks:5000}") int maxScanChunks) {
        this.embeddingService = embeddingService;
        this.redisTemplate = redisTemplate;
        this.articleMapper = articleMapper;
        this.folderMapper = folderMapper;
        this.defaultTopK = Math.max(1, Math.min(defaultTopK, 20));
        this.maxPerArticle = Math.max(1, maxPerArticle);
        this.minScore = minScore;
        this.maxScanChunks = Math.max(100, maxScanChunks);
    }

    public List<RetrievedChunk> retrieve(long userId, String query,
                                         String scopeType, Long scopeId, Integer requestedTopK) {
        String scope = scopeType == null || scopeType.isBlank()
                ? "ALL"
                : scopeType.trim().toUpperCase(Locale.ROOT);
        Set<Long> allowedArticleIds = resolveScope(userId, scope, scopeId);
        if (allowedArticleIds != null && allowedArticleIds.isEmpty()) {
            return List.of();
        }

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
            if (articleId == null) {
                continue;
            }
            if (allowedArticleIds != null && !allowedArticleIds.contains(articleId)) {
                continue;
            }
            float[] vector = decode(stringValue(fields.get("vector")));
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            double score = cosine(queryVector, vector);
            candidates.add(new Candidate(
                    articleId,
                    longValue(fields.get("chunk_id")),
                    stringValue(fields.get("content")),
                    intValue(fields.get("char_start")),
                    intValue(fields.get("char_end")),
                    score
            ));
        }

        double effectiveMinScore = model.startsWith("DEV-HASH") ? -1.0 : minScore;
        candidates.removeIf(candidate -> candidate.score() < effectiveMinScore);
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());

        int topK = requestedTopK == null ? defaultTopK : Math.max(1, Math.min(requestedTopK, 20));
        Map<Long, Integer> perArticle = new HashMap<>();
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (selected.size() >= topK) {
                break;
            }
            int count = perArticle.getOrDefault(candidate.articleId(), 0);
            if (count >= maxPerArticle) {
                continue;
            }
            perArticle.put(candidate.articleId(), count + 1);
            selected.add(candidate);
        }
        if (selected.isEmpty()) {
            return List.of();
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
                    candidate.score(),
                    candidate.charStart(),
                    candidate.charEnd()
            ));
        }
        return results;
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
        throw new BizException(ErrorCode.VALIDATION_ERROR, "当前仅支持 ALL / ARTICLE / FOLDER 范围");
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

    private record Candidate(
            long articleId,
            long chunkId,
            String content,
            int charStart,
            int charEnd,
            double score
    ) {
    }
}
