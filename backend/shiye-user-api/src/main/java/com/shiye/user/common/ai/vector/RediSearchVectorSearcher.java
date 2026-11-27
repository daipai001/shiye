package com.shiye.user.common.ai.vector;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RediSearch KNN 检索。覆盖个人 ALL、团队、标签/文件夹/单篇资料；
 * 索引不可用、回填未完成或过滤条件过大时由调用方回退 pipeline。
 */
@Component
public class RediSearchVectorSearcher {

    private static final Logger log = LoggerFactory.getLogger(RediSearchVectorSearcher.class);
    public static final int MAX_ARTICLE_FILTER = 200;
    private static final int MAX_KNN = 500;

    private final StringRedisTemplate redisTemplate;
    private final RediSearchVectorIndex index;
    private final MeterRegistry meterRegistry;

    public RediSearchVectorSearcher(StringRedisTemplate redisTemplate,
                                    RediSearchVectorIndex index,
                                    MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.index = index;
        this.meterRegistry = meterRegistry;
    }

    public boolean isEnabled() {
        return index.isEnabled();
    }

    public boolean isConfigured() {
        return index.isRedisearchEnabled();
    }

    public void recordFallback(String reason) {
        if (meterRegistry != null) {
            meterRegistry.counter("shiye.vector.hnsw.fallback",
                    "reason", reason == null ? "unknown" : reason).increment();
        }
    }

    public boolean isAuthoritative() {
        return isAuthoritative(null);
    }

    /**
     * 只有索引健康且对应范围回填完成时才使用 HNSW；否则整条链路回退 hash，
     * 避免“索引里只有一部分 chunk”导致静默丢召回。团队范围还要求 team_id
     * 回填完成。
     */
    public boolean isAuthoritative(Long teamId) {
        return index.isEnabled()
                && index.isBackfillComplete()
                && (teamId == null || index.isTeamBackfillComplete());
    }

    public List<VectorSearchHit> search(long userId, Long teamId, Set<Long> articleFilter,
                                        String model, float[] queryVector, int topK) {
        if (!isEnabled() || queryVector == null || topK <= 0) {
            return List.of();
        }
        if (articleFilter != null && articleFilter.size() > MAX_ARTICLE_FILTER) {
            return List.of();
        }
        int knn = Math.min(MAX_KNN, Math.max(topK, topK * 2));
        String query = buildQuery(userId, teamId, articleFilter, model, knn);
        RedisConnection connection = null;
        try {
            connection = redisTemplate.getConnectionFactory().getConnection();
            RedisAsyncCommands<byte[], byte[]> commands = RediSearchVectorIndex.async(connection);
            CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                    .add(RediSearchVectorIndex.INDEX_NAME)
                    .add(query)
                    .add("PARAMS").add(2).add("vec").add(binary(queryVector))
                    .add("SORTBY").add("score")
                    .add("RETURN").add(7)
                    .add("__key").add("article_id").add("chunk_id").add("content")
                    .add("char_start").add("char_end").add("score")
                    .add("DIALECT").add(2)
                    .add("LIMIT").add(0).add(knn);
            Object result = commands.dispatch(RediSearchVectorIndex.Ft.FT_SEARCH,
                    new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), args).get();
            return parse(result);
        } catch (Exception ex) {
            index.markUnavailable();
            recordFallback("knn_error");
            log.warn("RediSearch KNN failed, fallback to hash search: {}", ex.getMessage());
            return List.of();
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    static String buildQuery(long userId, Long teamId, Set<Long> articleFilter,
                             String model, int knn) {
        StringBuilder filter = new StringBuilder();
        filter.append(teamId == null
                ? "@user_id:{" + userId + "}"
                : "@team_id:{" + teamId + "}");
        filter.append(" @embedding_model:{").append(escapeTag(model)).append("}");
        if (articleFilter != null && !articleFilter.isEmpty()) {
            filter.append(" @article_id:{")
                    .append(articleFilter.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining("|")))
                    .append("}");
        }
        return "(" + filter + ")=>[KNN " + knn + " @vector_bin $vec AS score]";
    }

    List<VectorSearchHit> parse(Object result) {
        Map<String, Object> root = toMap(result);
        Object results = root.get("results");
        if (results instanceof List<?> list) {
            List<VectorSearchHit> hits = new ArrayList<>(list.size());
            for (Object item : list) {
                Map<String, Object> entry = toMap(item);
                Map<String, Object> attrs = toMap(entry.get("extra_attributes"));
                addHit(hits, string(entry.get("id")), attrs);
            }
            return hits;
        }
        return parseLegacy(result);
    }

    /**
     * 兼容 RESP2：FT.SEARCH 返回 [count, key, [field, value, ...], ...]。
     */
    private List<VectorSearchHit> parseLegacy(Object result) {
        if (!(result instanceof List<?> list) || list.size() < 2) {
            return List.of();
        }
        List<VectorSearchHit> hits = new ArrayList<>();
        for (int i = 1; i + 1 < list.size(); i += 2) {
            addHit(hits, string(list.get(i)), toMap(list.get(i + 1)));
        }
        return hits;
    }

    private void addHit(List<VectorSearchHit> hits, String key, Map<String, Object> attrs) {
        Long articleId = longValue(attrs.get("article_id"));
        Long chunkId = longValue(attrs.get("chunk_id"));
        if (articleId == null || chunkId == null) {
            Long fromKey = parseChunkId(key);
            if (fromKey == null) {
                return;
            }
            chunkId = fromKey;
        }
        double distance = doubleValue(attrs.get("score"));
        double score = Math.max(0, Math.min(1, 1.0 - distance));
        hits.add(new VectorSearchHit(
                articleId == null ? 0 : articleId,
                chunkId,
                string(attrs.get("content")),
                intValue(attrs.get("char_start")),
                intValue(attrs.get("char_end")),
                score));
    }

    private Long parseChunkId(String key) {
        if (key == null || !key.startsWith("kb:chunk:")) {
            return null;
        }
        try {
            return Long.parseLong(key.substring("kb:chunk:".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put(string(key), item));
            return converted;
        }
        if (value instanceof List<?> list) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (int i = 0; i + 1 < list.size(); i += 2) {
                String key = string(list.get(i));
                if (key != null) {
                    converted.put(key, list.get(i + 1));
                }
            }
            return converted;
        }
        return Map.of();
    }

    private String string(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        String text = string(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int intValue(Object value) {
        Long parsed = longValue(value);
        return parsed == null ? 0 : parsed.intValue();
    }

    private double doubleValue(Object value) {
        String text = string(value);
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private byte[] binary(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    static String escapeTag(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        String special = " ,.<>{}[]\"':;!@#$%^&*()-+=~|/\\";
        for (char ch : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (special.indexOf(ch) >= 0) {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }
}
