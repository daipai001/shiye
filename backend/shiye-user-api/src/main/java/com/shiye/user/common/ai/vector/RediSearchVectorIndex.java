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
import java.util.List;

/**
 * RediSearch HNSW 索引生命周期。
 *
 * <p>只有显式设置 VECTOR_STORE=redisearch 且 Redis 带 search 模块时启用；
 * 索引不可用时检索自动回退到原有 Redis Hash + 应用层余弦。</p>
 */
@Component
public class RediSearchVectorIndex {

    private static final Logger log = LoggerFactory.getLogger(RediSearchVectorIndex.class);
    public static final String INDEX_NAME = "idx:kb_chunks";

    private final StringRedisTemplate redisTemplate;
    private final boolean redisearchEnabled;
    private final int dimension;

    private volatile Boolean available;

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
        if (available == null) {
            synchronized (this) {
                if (available == null) {
                    available = ensureIndex();
                }
            }
        }
        return Boolean.TRUE.equals(available);
    }

    private boolean ensureIndex() {
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            try {
                RedisAsyncCommands<byte[], byte[]> commands = async(connection);
                CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                        .add(INDEX_NAME)
                        .add("ON").add("HASH")
                        .add("PREFIX").add(1).add("kb:chunk:")
                        .add("SCHEMA")
                        .add("user_id").add("TAG")
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
                log.info("RediSearch HNSW index ready: {} dim={}", INDEX_NAME, dimension);
                return true;
            } finally {
                connection.close();
            }
        } catch (Exception ex) {
            log.warn("RediSearch unavailable, fallback to hash vector search: {}", ex.getMessage());
            return false;
        }
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
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match("kb:chunk:*").count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, "vector_bin"))) {
                    continue;
                }
                Object encoded = redisTemplate.opsForHash().get(key, "vector");
                if (encoded == null) {
                    continue;
                }
                byte[] binary = Base64.getDecoder().decode(encoded.toString());
                if (binary.length != dimension * Float.BYTES) {
                    continue;
                }
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                byte[] field = "vector_bin".getBytes(StandardCharsets.UTF_8);
                redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    connection.hSet(keyBytes, field, binary);
                    return null;
                });
                migrated++;
            }
            log.info("backfilled {} vectors into RediSearch index", migrated);
        } catch (RuntimeException ex) {
            log.warn("vector backfill failed: {}", ex.getMessage());
        }
        return migrated;
    }

    @SuppressWarnings("unchecked")
    static RedisAsyncCommands<byte[], byte[]> async(RedisConnection connection) {
        return (RedisAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
    }

    enum Ft implements ProtocolKeyword {
        FT_CREATE,
        FT_SEARCH,
        FT_DROPINDEX;

        private final byte[] bytes = name().replace('_', '.').getBytes(StandardCharsets.US_ASCII);

        @Override
        public byte[] getBytes() {
            return bytes;
        }
    }
}
