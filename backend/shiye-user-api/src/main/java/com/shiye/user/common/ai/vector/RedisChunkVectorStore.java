package com.shiye.user.common.ai.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地 V1 向量存储：Redis Hash + 用户 chunk 集合。
 *
 * <p>本机 Redis 8 没有 RediSearch，因此先写 Hash 并维护 user 维度集合；
 * 查询阶段可在应用层做余弦检索。部署 Redis Stack 后可替换为 RediSearch HNSW 实现。</p>
 */
@Component
public class RedisChunkVectorStore implements ChunkVectorStore {

    private static final Logger log = LoggerFactory.getLogger(RedisChunkVectorStore.class);

    private final StringRedisTemplate redisTemplate;

    public RedisChunkVectorStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void upsert(long userId, long articleId, long chunkId, String model,
                       String content, int charStart, int charEnd, float[] vector) {
        String key = chunkKey(chunkId);
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("user_id", String.valueOf(userId));
            fields.put("article_id", String.valueOf(articleId));
            fields.put("chunk_id", String.valueOf(chunkId));
            fields.put("embedding_model", model);
            fields.put("content", content);
            fields.put("char_start", String.valueOf(charStart));
            fields.put("char_end", String.valueOf(charEnd));
            fields.put("vector", encode(vector));
            redisTemplate.opsForHash().putAll(key, fields);
            redisTemplate.opsForSet().add(userChunksKey(userId), String.valueOf(chunkId));
        } catch (RuntimeException ex) {
            log.warn("write vector failed for chunk {}: {}", chunkId, ex.getMessage());
            throw new VectorStoreException("向量库写入失败", ex);
        }
    }

    @Override
    public void delete(long userId, long chunkId) {
        try {
            redisTemplate.delete(chunkKey(chunkId));
            redisTemplate.opsForSet().remove(userChunksKey(userId), String.valueOf(chunkId));
        } catch (RuntimeException ex) {
            log.warn("delete vector failed for chunk {}: {}", chunkId, ex.getMessage());
        }
    }

    @Override
    public void addToTeam(long teamId, long chunkId) {
        try {
            redisTemplate.opsForSet().add(teamChunksKey(teamId), String.valueOf(chunkId));
        } catch (RuntimeException ex) {
            log.warn("add vector to team {} failed for chunk {}: {}",
                    teamId, chunkId, ex.getMessage());
            throw new VectorStoreException("团队向量索引写入失败", ex);
        }
    }

    @Override
    public void removeFromTeam(long teamId, long chunkId) {
        try {
            redisTemplate.opsForSet().remove(teamChunksKey(teamId), String.valueOf(chunkId));
        } catch (RuntimeException ex) {
            log.warn("remove vector from team {} failed for chunk {}: {}",
                    teamId, chunkId, ex.getMessage());
        }
    }

    private String chunkKey(long chunkId) {
        return "kb:chunk:" + chunkId;
    }

    private String userChunksKey(long userId) {
        return "kb:user:" + userId + ":chunks";
    }

    private String teamChunksKey(long teamId) {
        return "kb:team:" + teamId + ":chunks";
    }

    private String encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }
}
