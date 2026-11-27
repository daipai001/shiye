package com.shiye.user.common.ai.vector;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RediSearch HNSW 索引生命周期。
 *
 * <p>只有显式设置 VECTOR_STORE=redisearch 且 Redis 带 search 模块时启用；
 * 索引不可用、维度/字段不匹配或回填未完成时检索自动回退到原有
 * Redis Hash + 应用层余弦。</p>
 */
@Component
public class RediSearchVectorIndex {

    private static final Logger log = LoggerFactory.getLogger(RediSearchVectorIndex.class);
    public static final String INDEX_NAME = "idx:kb_chunks";
    private static final String BACKFILL_DONE_KEY = "shiye:vector:backfill:done";
    private static final String TEAM_BACKFILL_DONE_KEY = "shiye:vector:teams:backfill:done";
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "user_id", "team_id", "article_id", "chunk_id", "embedding_model", "vector_bin");

    private final StringRedisTemplate redisTemplate;
    private final boolean redisearchEnabled;
    private final int dimension;

    private volatile Boolean available;
    private volatile long retryAt;

    public RediSearchVectorIndex(
            StringRedisTemplate redisTemplate,
            @Value("${shiye.ai.vector.store:hash}") String store,
            @Value("${shiye.ai.embedding.dimension:1024}") int dimension) {
        this.redisTemplate = redisTemplate;
        this.redisearchEnabled = "redisearch".equalsIgnoreCase(store);
        this.dimension = Math.max(1, dimension);
    }

    public boolean isEnabled() {
        if (!redisearchEnabled) {
            return false;
        }
        if (Boolean.TRUE.equals(available)) {
            return true;
        }
        if (available == null || System.currentTimeMillis() >= retryAt) {
            synchronized (this) {
                if (available == null || System.currentTimeMillis() >= retryAt) {
                    available = ensureIndex();
                    retryAt = Boolean.TRUE.equals(available)
                            ? 0 : System.currentTimeMillis() + 60_000L;
                }
            }
        }
        return Boolean.TRUE.equals(available);
    }

    public void markUnavailable() {
        available = false;
        retryAt = System.currentTimeMillis() + 60_000L;
    }

    public boolean isBackfillComplete() {
        try {
            String value = redisTemplate.opsForValue().get(BACKFILL_DONE_KEY);
            return String.valueOf(dimension).equals(value);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void markBackfillComplete() {
        try {
            redisTemplate.opsForValue().set(BACKFILL_DONE_KEY, String.valueOf(dimension));
        } catch (RuntimeException ex) {
            log.warn("mark vector backfill complete failed: {}", ex.getMessage());
        }
    }

    public boolean isTeamBackfillComplete() {
        try {
            return "1".equals(redisTemplate.opsForValue().get(TEAM_BACKFILL_DONE_KEY));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void markTeamBackfillComplete() {
        try {
            redisTemplate.opsForValue().set(TEAM_BACKFILL_DONE_KEY, "1");
        } catch (RuntimeException ex) {
            log.warn("mark team vector backfill complete failed: {}", ex.getMessage());
        }
    }

    public int dimension() {
        return dimension;
    }

    private boolean ensureIndex() {
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            try {
                RedisAsyncCommands<byte[], byte[]> commands = async(connection);
                IndexInfo info = readIndexInfo(commands);
                if (info != null && info.valid(dimension)) {
                    log.info("RediSearch HNSW index ready: {} dim={}", INDEX_NAME, dimension);
                    return true;
                }
                if (info != null) {
                    log.warn("RediSearch index mismatch, rebuilding: {} fields={} dim={} expectedDim={}",
                            INDEX_NAME, info.fields(), info.dimension(), dimension);
                    dropIndex(commands);
                }
                createIndex(commands);
                log.info("RediSearch HNSW index created: {} dim={}", INDEX_NAME, dimension);
                return true;
            } finally {
                connection.close();
            }
        } catch (Exception ex) {
            log.warn("RediSearch unavailable, fallback to hash vector search: {}", ex.getMessage());
            return false;
        }
    }

    private IndexInfo readIndexInfo(RedisAsyncCommands<byte[], byte[]> commands) {
        try {
            CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                    .add(INDEX_NAME);
            Object result = commands.dispatch(Ft.FT_INFO,
                    new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), args).get();
            return parseIndexInfo(result);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (message.contains("unknown index") || message.contains("no such index")) {
                return null;
            }
            throw new IllegalStateException("FT.INFO failed: " + ex.getMessage(), ex);
        }
    }

    static IndexInfo parseIndexInfo(Object result) {
        Map<String, Object> info = toMap(result);
        if (info.isEmpty()) {
            return null;
        }
        Object attributesValue = info.get("attributes");
        if (!(attributesValue instanceof List<?> attributes)) {
            return new IndexInfo(Set.of(), null);
        }
        Set<String> fields = new HashSet<>();
        Integer dim = null;
        for (Object item : attributes) {
            Map<String, Object> attribute = toMap(item);
            String identifier = string(attribute.get("identifier"));
            String type = string(attribute.get("type"));
            if (identifier != null) {
                fields.add(identifier);
            }
            if ("vector_bin".equals(identifier) || "VECTOR".equalsIgnoreCase(type)) {
                Long parsed = longValue(attribute.get("dim"));
                if (parsed != null) {
                    dim = parsed.intValue();
                }
            }
        }
        return new IndexInfo(fields, dim);
    }

    private void createIndex(RedisAsyncCommands<byte[], byte[]> commands) throws Exception {
        CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                .add(INDEX_NAME)
                .add("ON").add("HASH")
                .add("PREFIX").add(1).add("kb:chunk:")
                .add("SCHEMA")
                .add("user_id").add("TAG")
                .add("team_id").add("TAG")
                .add("article_id").add("TAG")
                .add("chunk_id").add("NUMERIC")
                .add("embedding_model").add("TAG")
                .add("vector_bin").add("VECTOR")
                .add("HNSW").add(6)
                .add("TYPE").add("FLOAT32")
                .add("DIM").add(dimension)
                .add("DISTANCE_METRIC").add("COSINE");
        try {
            commands.dispatch(Ft.FT_CREATE,
                    new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), args).get();
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (!message.contains("already exists")) {
                throw ex;
            }
        }
    }

    private void dropIndex(RedisAsyncCommands<byte[], byte[]> commands) throws Exception {
        CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                .add(INDEX_NAME);
        commands.dispatch(Ft.FT_DROPINDEX,
                new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), args).get();
    }

    /**
     * 一次性把旧 Hash 里的 base64 vector 复制成 RedisSearch 需要的二进制字段。
     * 只应在首次启用 VECTOR_STORE=redisearch 时通过 VECTOR_BACKFILL=true 运行。
     */
    public int backfillExistingVectors() {
        if (!isEnabled()) {
            return 0;
        }
        int migrated = 0;
        int failed = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match("kb:chunk:*").count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    if (Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, "vector_bin"))) {
                        continue;
                    }
                    Object encoded = redisTemplate.opsForHash().get(key, "vector");
                    if (encoded == null) {
                        continue;
                    }
                    byte[] binary = Base64.getDecoder().decode(encoded.toString());
                    if (binary.length != dimension * Float.BYTES) {
                        failed++;
                        continue;
                    }
                    writeVectorBin(key, binary);
                    migrated++;
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("backfill vector failed for {}: {}", key, ex.getMessage());
                }
            }
            log.info("backfilled {} vectors into RediSearch index, failed={}", migrated, failed);
            if (failed == 0) {
                markBackfillComplete();
            }
        } catch (RuntimeException ex) {
            log.warn("vector backfill failed: {}", ex.getMessage());
        }
        return migrated;
    }

    /**
     * 把已有团队集合里的 chunk 补上 team_id，并补齐 vector_bin。
     * 团队检索只有在回填完成后才会启用，避免只召回部分团队切片。
     */
    public int backfillTeamMemberships() {
        if (!isEnabled()) {
            return 0;
        }
        int linked = 0;
        int failed = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match("kb:team:*:chunks").count(200).build())) {
            while (cursor.hasNext()) {
                String setKey = cursor.next();
                Long teamId = parseTeamId(setKey);
                if (teamId == null) {
                    continue;
                }
                Set<String> chunkIds = redisTemplate.opsForSet().members(setKey);
                if (chunkIds == null) {
                    continue;
                }
                for (String chunkId : chunkIds) {
                    String key = "kb:chunk:" + chunkId;
                    try {
                        redisTemplate.opsForHash().put(key, "team_id", String.valueOf(teamId));
                        if (!ensureVectorBin(key)) {
                            failed++;
                        }
                        linked++;
                    } catch (RuntimeException ex) {
                        failed++;
                        log.warn("backfill team {} chunk {} failed: {}",
                                teamId, chunkId, ex.getMessage());
                    }
                }
            }
            log.info("backfilled {} team chunk links, failed={}", linked, failed);
            if (failed == 0) {
                markTeamBackfillComplete();
            }
        } catch (RuntimeException ex) {
            log.warn("team vector backfill failed: {}", ex.getMessage());
        }
        return linked;
    }

    private boolean ensureVectorBin(String key) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, "vector_bin"))) {
                return true;
            }
            Object encoded = redisTemplate.opsForHash().get(key, "vector");
            if (encoded == null) {
                return false;
            }
            byte[] binary = Base64.getDecoder().decode(encoded.toString());
            if (binary.length != dimension * Float.BYTES) {
                return false;
            }
            writeVectorBin(key, binary);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void writeVectorBin(String key, byte[] binary) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] field = "vector_bin".getBytes(StandardCharsets.UTF_8);
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.hSet(keyBytes, field, binary);
            return null;
        });
    }

    private static Long parseTeamId(String setKey) {
        String prefix = "kb:team:";
        String suffix = ":chunks";
        if (setKey == null || !setKey.startsWith(prefix) || !setKey.endsWith(suffix)) {
            return null;
        }
        try {
            return Long.parseLong(setKey.substring(prefix.length(),
                    setKey.length() - suffix.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Map<String, Object> toMap(Object value) {
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

    static String string(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : String.valueOf(value);
    }

    static Long longValue(Object value) {
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

    public record IndexInfo(Set<String> fields, Integer dimension) {

        public boolean valid(int expectedDimension) {
            return fields.containsAll(REQUIRED_FIELDS)
                    && dimension != null
                    && dimension == expectedDimension;
        }
    }

    @SuppressWarnings("unchecked")
    static RedisAsyncCommands<byte[], byte[]> async(RedisConnection connection) {
        return (RedisAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
    }

    enum Ft implements ProtocolKeyword {
        FT_CREATE,
        FT_SEARCH,
        FT_INFO,
        FT_DROPINDEX;

        private final byte[] bytes = name().replace('_', '.').getBytes(StandardCharsets.US_ASCII);

        @Override
        public byte[] getBytes() {
            return bytes;
        }
    }
}
